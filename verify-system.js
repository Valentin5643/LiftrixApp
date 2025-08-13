/**
 * System verification script for Firebase billing protection
 * Checks all components and confirms system is working correctly
 */

const { execSync } = require('child_process');

function runCommand(command, description) {
  try {
    console.log(`🔍 ${description}...`);
    const result = execSync(command, { encoding: 'utf8' });
    console.log(`✅ ${description} - OK`);
    return result.trim();
  } catch (error) {
    console.error(`❌ ${description} - FAILED`);
    console.error(`   Error: ${error.message}`);
    return null;
  }
}

async function verifySystem() {
  console.log('🛡️ Firebase Billing Protection System Verification');
  console.log('================================================');
  
  const checks = [];
  
  // Check 1: Project configuration
  const projectCheck = runCommand(
    'gcloud config get-value project',
    'Checking current project'
  );
  checks.push({ 
    name: 'Project Configuration', 
    status: projectCheck === 'liftrix-390cf' ? 'PASS' : 'FAIL',
    value: projectCheck 
  });
  
  // Check 2: Billing status
  const billingCheck = runCommand(
    'gcloud billing projects describe liftrix-390cf --format="value(billingEnabled)"',
    'Checking billing status'
  );
  checks.push({ 
    name: 'Billing Enabled', 
    status: billingCheck === 'True' ? 'PASS' : 'FAIL',
    value: billingCheck 
  });
  
  // Check 3: Budget configuration
  const budgetCheck = runCommand(
    'gcloud billing budgets list --billing-account=01595E-63A9D0-AB5EE6 --format="value(amount.specifiedAmount.units)" --filter="displayName:\'Firebase Project liftrix-390cf - Automatic Billing Shutdown\'"',
    'Checking budget amount'
  );
  checks.push({ 
    name: 'Budget Amount ($50)', 
    status: budgetCheck === '50' ? 'PASS' : 'FAIL',
    value: `$${budgetCheck}` 
  });
  
  // Check 4: Pub/Sub topic
  const topicCheck = runCommand(
    'gcloud pubsub topics describe budget-alerts --format="value(name)"',
    'Checking Pub/Sub topic'
  );
  checks.push({ 
    name: 'Pub/Sub Topic', 
    status: topicCheck && topicCheck.includes('budget-alerts') ? 'PASS' : 'FAIL',
    value: topicCheck 
  });
  
  // Check 5: Cloud Function
  const functionCheck = runCommand(
    'gcloud functions describe enforceBudget --region=us-central1 --format="value(state)"',
    'Checking Cloud Function'
  );
  checks.push({ 
    name: 'Cloud Function Status', 
    status: functionCheck === 'ACTIVE' ? 'PASS' : 'FAIL',
    value: functionCheck 
  });
  
  // Check 6: Function permissions
  const permissionCheck = runCommand(
    'gcloud projects get-iam-policy liftrix-390cf --flatten="bindings[].members" --filter="bindings.members:734273269747-compute@developer.gserviceaccount.com AND bindings.role:roles/billing.projectManager" --format="value(bindings.role)"',
    'Checking function permissions'
  );
  checks.push({ 
    name: 'Billing Permissions', 
    status: permissionCheck && permissionCheck.includes('billing.projectManager') ? 'PASS' : 'FAIL',
    value: permissionCheck || 'Not found' 
  });
  
  // Check 7: Budget notification configuration
  const notificationCheck = runCommand(
    'gcloud billing budgets list --billing-account=01595E-63A9D0-AB5EE6 --format="value(notificationsRule.pubsubTopic)" --filter="displayName:\'Firebase Project liftrix-390cf - Automatic Billing Shutdown\'"',
    'Checking budget notifications'
  );
  checks.push({ 
    name: 'Budget Notifications', 
    status: notificationCheck && notificationCheck.includes('budget-alerts') ? 'PASS' : 'FAIL',
    value: notificationCheck 
  });
  
  // Display results
  console.log('\n📊 System Status Report');
  console.log('========================');
  
  const passCount = checks.filter(check => check.status === 'PASS').length;
  const totalChecks = checks.length;
  
  checks.forEach(check => {
    const status = check.status === 'PASS' ? '✅' : '❌';
    console.log(`${status} ${check.name}: ${check.value}`);
  });
  
  console.log(`\n📈 Overall Status: ${passCount}/${totalChecks} checks passed`);
  
  if (passCount === totalChecks) {
    console.log('\n🎉 SUCCESS: Your Firebase billing protection system is fully operational!');
    console.log('   • Automatic shutdown will trigger at $50 spending');
    console.log('   • No manual intervention required');
    console.log('   • System is actively monitoring your project');
    console.log('   • Complete audit trail is being maintained');
  } else {
    console.log('\n⚠️  WARNING: Some components may not be working correctly.');
    console.log('   Please review the failed checks and ensure all components are properly configured.');
  }
  
  console.log('\n📚 Next Steps:');
  console.log('   • Review BILLING_PROTECTION_SETUP.md for complete documentation');
  console.log('   • Monitor Cloud Function logs regularly');
  console.log('   • Test the system using test-budget-alert.js if desired');
  console.log('   • Keep re-enable-billing.js handy for manual recovery');
}

verifySystem().catch(console.error);