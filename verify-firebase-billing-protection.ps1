param(
    [string]$ProjectId = "liftrix-390cf",
    [string]$BillingAccountId = "01D860-9ECE2B-6852D6",
    [string]$BudgetId = "79780028-c2c7-48b0-b53b-9ba80ed44709",
    [string]$TopicName = "budget-alerts",
    [string]$FunctionName = "enforceBudget",
    [string]$Region = "us-central1",
    [string]$ExpectedFunctionServiceAccount = "734273269747-compute@developer.gserviceaccount.com",
    [decimal]$ExpectedBudgetAmount = 50,
    [string]$ExpectedCurrency = "USD"
)

$ErrorActionPreference = "Stop"

$PassCount = 0
$WarnCount = 0
$FailCount = 0

function Add-Pass($msg) {
    $script:PassCount++
    Write-Host "[PASS] $msg" -ForegroundColor Green
}

function Add-Warn($msg) {
    $script:WarnCount++
    Write-Host "[WARN] $msg" -ForegroundColor Yellow
}

function Add-Fail($msg) {
    $script:FailCount++
    Write-Host "[FAIL] $msg" -ForegroundColor Red
}

function Run-GcloudJson {
    param(
        [string[]]$GcloudArgs,
        [switch]$AllowFail
    )

    $fullArgs = @($GcloudArgs) + @("--format=json")
    $output = & gcloud @fullArgs 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        if ($AllowFail) {
            return @{
                Ok = $false
                Error = ($output | Out-String)
                Data = $null
            }
        }

        throw "gcloud $($fullArgs -join ' ') failed:`n$($output | Out-String)"
    }

    $text = ($output | Out-String).Trim()

    if ([string]::IsNullOrWhiteSpace($text)) {
        return @{
            Ok = $true
            Error = $null
            Data = $null
        }
    }

    return @{
        Ok = $true
        Error = $null
        Data = ($text | ConvertFrom-Json)
    }
}

function Has-IamRole {
    param(
        $Policy,
        [string]$Member,
        [string[]]$AllowedRoles
    )

    if ($null -eq $Policy -or $null -eq $Policy.bindings) {
        return $false
    }

    foreach ($binding in $Policy.bindings) {
        if ($AllowedRoles -contains $binding.role) {
            if ($binding.members -contains $Member) {
                return $true
            }
        }
    }

    return $false
}

Write-Host ""
Write-Host "Firebase Billing Protection Verification" -ForegroundColor Cyan
Write-Host "Project: $ProjectId"
Write-Host "Billing account: $BillingAccountId"
Write-Host "Budget ID: $BudgetId"
Write-Host ""

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    Add-Fail "gcloud CLI is not installed or not in PATH."
    exit 1
} else {
    Add-Pass "gcloud CLI found."
}

try {
    $auth = Run-GcloudJson -GcloudArgs @("auth", "list", "--filter=status:ACTIVE")

    if ($null -ne $auth.Data -and @($auth.Data).Count -gt 0) {
        Add-Pass "gcloud has an active authenticated account."
    } else {
        Add-Fail "No active gcloud account. Run: gcloud auth login"
    }
} catch {
    Add-Fail "Could not check gcloud authentication: $_"
}

$projectNumber = $null

try {
    $project = Run-GcloudJson -GcloudArgs @("projects", "describe", $ProjectId)
    $projectNumber = $project.Data.projectNumber
    Add-Pass "Project exists: $ProjectId / project number $projectNumber"
} catch {
    Add-Fail "Project not found or inaccessible: $ProjectId. Run: gcloud auth login"
}

$requiredApis = @(
    "cloudbilling.googleapis.com",
    "billingbudgets.googleapis.com",
    "pubsub.googleapis.com",
    "cloudfunctions.googleapis.com"
)

try {
    $services = Run-GcloudJson -GcloudArgs @("services", "list", "--enabled", "--project=$ProjectId")
    $enabledApis = @($services.Data | ForEach-Object { $_.config.name })

    foreach ($api in $requiredApis) {
        if ($enabledApis -contains $api) {
            Add-Pass "API enabled: $api"
        } else {
            Add-Fail "API missing: $api"
        }
    }

    if ($enabledApis -contains "run.googleapis.com") {
        Add-Pass "Cloud Run API enabled. Useful if this is a Gen 2 function."
    } else {
        Add-Warn "Cloud Run API not enabled. Okay for Gen 1, but Gen 2 usually needs it."
    }

    if ($enabledApis -contains "eventarc.googleapis.com") {
        Add-Pass "Eventarc API enabled. Useful if this is a Gen 2 Pub/Sub-triggered function."
    } else {
        Add-Warn "Eventarc API not enabled. Okay for Gen 1, but Gen 2 usually needs it."
    }
} catch {
    Add-Fail "Could not list enabled APIs: $_"
}

try {
    $billing = Run-GcloudJson -GcloudArgs @("billing", "projects", "describe", $ProjectId)
    $expectedBillingName = "billingAccounts/$BillingAccountId"

    if ($billing.Data.billingEnabled -eq $true) {
        Add-Pass "Billing is currently enabled on project."
    } else {
        Add-Fail "Billing is currently disabled on project."
    }

    if ($billing.Data.billingAccountName -eq $expectedBillingName) {
        Add-Pass "Project is linked to expected billing account: $BillingAccountId"
    } else {
        Add-Fail "Project billing account mismatch. Found: $($billing.Data.billingAccountName), expected: $expectedBillingName"
    }
} catch {
    Add-Fail "Could not read project billing status: $_"
}

$expectedTopicFullName = "projects/$ProjectId/topics/$TopicName"

try {
    $topic = Run-GcloudJson -GcloudArgs @("pubsub", "topics", "describe", $TopicName, "--project=$ProjectId")

    if ($topic.Data.name -eq $expectedTopicFullName) {
        Add-Pass "Pub/Sub topic exists: $expectedTopicFullName"
    } else {
        Add-Warn "Pub/Sub topic found, but full name is unexpected: $($topic.Data.name)"
    }
} catch {
    Add-Fail "Pub/Sub topic missing or inaccessible: $TopicName"
}

$budget = $null

try {
    $budgets = Run-GcloudJson -GcloudArgs @("billing", "budgets", "list", "--billing-account=$BillingAccountId")
    $budget = @($budgets.Data | Where-Object { $_.name -match "/budgets/$BudgetId$" })[0]

    if ($null -ne $budget) {
        Add-Pass "Budget exists: $BudgetId"

        $amount = [decimal]$budget.amount.specifiedAmount.units
        $currency = $budget.amount.specifiedAmount.currencyCode

        if ($amount -eq $ExpectedBudgetAmount -and $currency -eq $ExpectedCurrency) {
            Add-Pass "Budget amount is $ExpectedBudgetAmount $ExpectedCurrency."
        } else {
            Add-Fail "Budget amount mismatch. Found: $amount $currency, expected: $ExpectedBudgetAmount $ExpectedCurrency."
        }

        $thresholds = @($budget.thresholdRules | ForEach-Object { [decimal]$_.thresholdPercent })

        if ($thresholds -contains 0.5) {
            Add-Pass "Budget has 50% threshold."
        } else {
            Add-Warn "Budget does not show 50% threshold."
        }

        if ($thresholds -contains 0.9) {
            Add-Pass "Budget has 90% threshold."
        } else {
            Add-Warn "Budget does not show 90% threshold."
        }

        if ($thresholds -contains 1.0) {
            Add-Pass "Budget has 100% threshold."
        } else {
            Add-Fail "Budget does not show 100% threshold."
        }

        $budgetTopic = $budget.allUpdatesRule.pubsubTopic

        if ($budgetTopic -eq $expectedTopicFullName) {
            Add-Pass "Budget publishes updates to expected Pub/Sub topic."
        } else {
            Add-Fail "Budget Pub/Sub topic mismatch. Found: '$budgetTopic', expected: '$expectedTopicFullName'"
        }

        $projectFilterOk = $false

        if ($null -ne $budget.budgetFilter.projects -and $null -ne $projectNumber) {
            $budgetProjects = @($budget.budgetFilter.projects)

            if ($budgetProjects -contains "projects/$projectNumber" -or $budgetProjects -contains "projects/$ProjectId") {
                $projectFilterOk = $true
            }
        }

        if ($projectFilterOk) {
            Add-Pass "Budget appears scoped to the Liftrix project."
        } else {
            Add-Warn "Budget does not clearly appear scoped only to this project."
        }
    } else {
        Add-Fail "Budget ID not found under billing account: $BudgetId"
    }
} catch {
    Add-Fail "Could not verify budget configuration: $_"
}

$function = $null
$functionGen = $null

try {
    $fn2 = Run-GcloudJson -GcloudArgs @("functions", "describe", $FunctionName, "--gen2", "--region=$Region", "--project=$ProjectId") -AllowFail

    if ($fn2.Ok) {
        $function = $fn2.Data
        $functionGen = "gen2"
        Add-Pass "Cloud Function found as Gen 2."
    } else {
        $fn1 = Run-GcloudJson -GcloudArgs @("functions", "describe", $FunctionName, "--region=$Region", "--project=$ProjectId") -AllowFail

        if ($fn1.Ok) {
            $function = $fn1.Data
            $functionGen = "gen1"
            Add-Pass "Cloud Function found as Gen 1."
        } else {
            Add-Fail "Cloud Function not found as Gen 1 or Gen 2: $FunctionName"
        }
    }
} catch {
    Add-Fail "Could not describe Cloud Function: $_"
}

$actualServiceAccount = $ExpectedFunctionServiceAccount

if ($null -ne $function) {
    if ($functionGen -eq "gen2") {
        if ($function.state -eq "ACTIVE") {
            Add-Pass "Function state is ACTIVE."
        } else {
            Add-Fail "Function state is not ACTIVE. Found: $($function.state)"
        }

        if ($function.buildConfig.runtime -match "nodejs") {
            Add-Pass "Function runtime is Node.js: $($function.buildConfig.runtime)"
        } else {
            Add-Warn "Function runtime is not Node.js. Found: $($function.buildConfig.runtime)"
        }

        if ($function.serviceConfig.serviceAccountEmail) {
            $actualServiceAccount = $function.serviceConfig.serviceAccountEmail

            if ($actualServiceAccount -eq $ExpectedFunctionServiceAccount) {
                Add-Pass "Function service account matches expected account."
            } else {
                Add-Warn "Function service account differs. Expected $ExpectedFunctionServiceAccount, found $actualServiceAccount."
            }
        }

        $triggerTopic = $function.eventTrigger.pubsubTopic

        if ($triggerTopic -eq $expectedTopicFullName) {
            Add-Pass "Function is triggered by expected Pub/Sub topic."
        } else {
            Add-Fail "Function trigger topic mismatch. Found: '$triggerTopic', expected: '$expectedTopicFullName'"
        }
    }

    if ($functionGen -eq "gen1") {
        if ($function.status -eq "ACTIVE") {
            Add-Pass "Function status is ACTIVE."
        } else {
            Add-Fail "Function status is not ACTIVE. Found: $($function.status)"
        }

        if ($function.runtime -match "nodejs") {
            Add-Pass "Function runtime is Node.js: $($function.runtime)"
        } else {
            Add-Warn "Function runtime is not Node.js. Found: $($function.runtime)"
        }

        if ($function.serviceAccountEmail) {
            $actualServiceAccount = $function.serviceAccountEmail

            if ($actualServiceAccount -eq $ExpectedFunctionServiceAccount) {
                Add-Pass "Function service account matches expected account."
            } else {
                Add-Warn "Function service account differs. Expected $ExpectedFunctionServiceAccount, found $actualServiceAccount."
            }
        }

        $triggerResource = $function.eventTrigger.resource

        if ($triggerResource -eq $expectedTopicFullName -or $triggerResource -match "/topics/$TopicName$") {
            Add-Pass "Function is triggered by expected Pub/Sub topic."
        } else {
            Add-Fail "Function trigger topic mismatch. Found: '$triggerResource', expected topic: '$expectedTopicFullName'"
        }
    }
}

$member = "serviceAccount:$actualServiceAccount"
$billingRoles = @(
    "roles/billing.projectManager",
    "roles/billing.admin"
)

try {
    $billingPolicy = Run-GcloudJson -GcloudArgs @("billing", "accounts", "get-iam-policy", $BillingAccountId)

    if (Has-IamRole -Policy $billingPolicy.Data -Member $member -AllowedRoles $billingRoles) {
        Add-Pass "Function service account has billing permissions on billing account."
    } else {
        Add-Fail "Function service account does not appear to have roles/billing.projectManager or roles/billing.admin on billing account."
    }
} catch {
    Add-Warn "Could not check billing account IAM policy. You may lack permission to view it: $_"
}

try {
    $projectPolicy = Run-GcloudJson -GcloudArgs @("projects", "get-iam-policy", $ProjectId)

    if (Has-IamRole -Policy $projectPolicy.Data -Member $member -AllowedRoles $billingRoles) {
        Add-Warn "Function service account has billing role on project IAM. Usually the key permission should be on the billing account."
    } else {
        Add-Pass "No misleading project-level billing.projectManager binding found."
    }
} catch {
    Add-Warn "Could not check project IAM policy: $_"
}

$localFunctionPath = Join-Path (Get-Location) "cloud-functions\budget-enforcer\index.js"

if (Test-Path $localFunctionPath) {
    $source = Get-Content $localFunctionPath -Raw

    if ($source -match "costAmount" -and $source -match "budgetAmount") {
        Add-Pass "Local function source references costAmount and budgetAmount."
    } else {
        Add-Warn "Local function source does not clearly reference costAmount and budgetAmount."
    }

    if ($source -match "billingAccountName") {
        Add-Pass "Local function source references billingAccountName."
    } else {
        Add-Warn "Local function source does not clearly reference billingAccountName."
    }
} else {
    Add-Warn "Local function source not found at cloud-functions\budget-enforcer\index.js."
}

try {
    if ($functionGen -eq "gen2") {
        $logs = Run-GcloudJson -GcloudArgs @("functions", "logs", "read", $FunctionName, "--gen2", "--region=$Region", "--project=$ProjectId", "--limit=20") -AllowFail
    } else {
        $logs = Run-GcloudJson -GcloudArgs @("functions", "logs", "read", $FunctionName, "--region=$Region", "--project=$ProjectId", "--limit=20") -AllowFail
    }

    if ($logs.Ok) {
        Add-Pass "Function logs are readable."
    } else {
        Add-Warn "Could not read function logs. This may be a permissions issue."
    }
} catch {
    Add-Warn "Function logs check skipped or failed: $_"
}

Write-Host ""
Write-Host "Verification Summary" -ForegroundColor Cyan
Write-Host "PASS: $PassCount" -ForegroundColor Green
Write-Host "WARN: $WarnCount" -ForegroundColor Yellow
Write-Host "FAIL: $FailCount" -ForegroundColor Red
Write-Host ""

if ($FailCount -eq 0) {
    Write-Host "RESULT: Billing protection setup looks valid." -ForegroundColor Green
    Write-Host "Note: Budget alerts are delayed, so this is protection, not a perfect hard $50 cap."
    exit 0
} else {
    Write-Host "RESULT: Billing protection setup has problems that should be fixed." -ForegroundColor Red
    exit 1
}