/**
 * Script to manually re-enable billing after automatic shutdown
 * 
 * IMPORTANT: Only run this script when you're ready to re-enable billing charges.
 * Make sure you understand why billing was disabled and have taken steps to control costs.
 */

const { google } = require('googleapis');

// Configuration
const PROJECT_ID = 'liftrix-390cf';
const BILLING_ACCOUNT_ID = '01595E-63A9D0-AB5EE6'; // Firebase Payment account

// Initialize Google APIs
const cloudbilling = google.cloudbilling('v1');

async function reEnableBilling() {
  try {
    console.log('🔄 Re-enabling Billing for Project:', PROJECT_ID);
    console.log('=============================================');
    
    // Get OAuth2 client
    const auth = new google.auth.GoogleAuth({
      scopes: ['https://www.googleapis.com/auth/cloud-billing']
    });
    
    const authClient = await auth.getClient();
    
    // Check current billing status
    console.log('📊 Checking current billing status...');
    const billingInfoResponse = await cloudbilling.projects.getBillingInfo({
      auth: authClient,
      name: `projects/${PROJECT_ID}`,
    });
    
    const currentBillingAccount = billingInfoResponse.data.billingAccountName;
    console.log('Current billing account:', currentBillingAccount || 'DISABLED');
    
    if (currentBillingAccount) {
      console.log('✅ Billing is already enabled for this project.');
      console.log('   No action needed.');
      return;
    }
    
    // Confirm re-enablement
    console.log('\n⚠️  WARNING: This will re-enable billing charges for your project.');
    console.log('   Make sure you have reviewed and addressed any cost concerns.');
    console.log('   Your Firebase services will start incurring charges again.');
    
    if (!process.argv.includes('--confirm')) {
      console.log('\n❌ Re-enablement cancelled for safety.');
      console.log('   To proceed, run: node re-enable-billing.js --confirm');
      return;
    }
    
    // Re-enable billing
    console.log('\n🔄 Re-enabling billing...');
    const response = await cloudbilling.projects.updateBillingInfo({
      auth: authClient,
      name: `projects/${PROJECT_ID}`,
      requestBody: {
        billingAccountName: `billingAccounts/${BILLING_ACCOUNT_ID}`,
      },
    });
    
    console.log('✅ Billing successfully re-enabled!');
    console.log('   Billing account:', response.data.billingAccountName);
    
    // Log the re-enablement
    const logData = {
      timestamp: new Date().toISOString(),
      action: 'BILLING_RE_ENABLED',
      projectId: PROJECT_ID,
      billingAccount: response.data.billingAccountName,
      enabledBy: process.env.USER || process.env.USERNAME || 'unknown'
    };
    
    console.log('\n📝 Action logged:', JSON.stringify(logData, null, 2));
    
    console.log('\n🔍 Next steps:');
    console.log('  1. Monitor your spending closely');
    console.log('  2. Review Firebase usage in the console');
    console.log('  3. Consider implementing cost optimization measures');
    console.log('  4. The automatic shutdown system is still active');
    
  } catch (error) {
    console.error('❌ Error re-enabling billing:', error);
    
    if (error.code === 403) {
      console.error('   Permission denied. Make sure you have billing administrator access.');
    } else if (error.code === 400) {
      console.error('   Invalid request. Check the billing account ID.');
    }
    
    process.exit(1);
  }
}

// Safety check
if (process.argv.includes('--confirm')) {
  reEnableBilling();
} else {
  console.log('⚠️  BILLING RE-ENABLEMENT SCRIPT');
  console.log('================================');
  console.log('This script will re-enable billing for your Firebase project.');
  console.log('Charges will resume for all Firebase services.');
  console.log('');
  console.log('To proceed, run: node re-enable-billing.js --confirm');
  console.log('');
  console.log('Before re-enabling, consider:');
  console.log('• Why was billing disabled? (check Cloud Function logs)');
  console.log('• Have you addressed the cost concerns?');
  console.log('• Are you prepared to monitor spending closely?');
}