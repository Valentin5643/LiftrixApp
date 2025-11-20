package com.example.liftrix.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Issue registry for LiftrixApp custom lint rules.
 *
 * These rules enforce architectural patterns critical for app quality:
 * 1. Room-First architecture (no direct Firebase in UseCases)
 * 2. Consistent error handling (LiftrixResult pattern)
 * 3. User data security (user scoping in DAO queries)
 */
@Suppress("UnstableApiUsage")
class LiftrixIssueRegistry : IssueRegistry() {

    override val issues: List<Issue>
        get() = listOf(
            // Architecture enforcement
            NoDirectFirebaseInjectionDetector.ISSUE,

            // Error handling consistency
            EnforceLiftrixResultDetector.ISSUE,
            EnforceLiftrixResultDetector.ISSUE_LEGACY_RESULT,

            // Security - user data isolation
            EnforceUserScopingDetector.ISSUE
        )

    override val api: Int
        get() = CURRENT_API

    override val minApi: Int
        get() = 12 // Minimum supported lint API

    override val vendor: Vendor
        get() = Vendor(
            vendorName = "LiftrixApp",
            feedbackUrl = "https://github.com/liftrix/android/issues",
            contact = "dev@liftrix.com"
        )
}
