const { google } = require('googleapis');
const functions = require('@google-cloud/functions-framework');

// Configuration
const PROJECT_ID = process.env.GOOGLE_CLOUD_PROJECT;
const BUDGET_AMOUNT = 50; // $50 USD budget limit

// Initialize Google APIs
const cloudbilling = google.cloudbilling('v1');

/**
 * Cloud Function triggered by Pub/Sub budget alerts
 * Automatically disables billing when budget is exceeded
 */
functions.cloudEvent('enforceBudget', async (cloudEvent) => {
  console.log('Budget alert received:', JSON.stringify(cloudEvent, null, 2));
  
  try {
    // Parse the Pub/Sub message
    const pubsubMessage = cloudEvent.data;
    let budgetData;
    
    if (pubsubMessage && pubsubMessage.message && pubsubMessage.message.data) {
      // Decode base64 message data
      const messageData = Buffer.from(pubsubMessage.message.data, 'base64').toString();
      budgetData = JSON.parse(messageData);
    } else {
      console.error('Invalid Pub/Sub message format');
      return;
    }
    
    console.log('Budget notification data:', JSON.stringify(budgetData, null, 2));
    
    // Extract cost and budget information
    const costAmount = budgetData.costAmount || 0;
    const budgetAmount = budgetData.budgetAmount || BUDGET_AMOUNT;
    const alertThresholdExceeded = budgetData.alertThresholdExceeded;
    
    console.log(`Current cost: $${costAmount}, Budget: $${budgetAmount}, Alert threshold exceeded: ${alertThresholdExceeded}`);
    
    // Check if we need to disable billing
    const shouldDisableBilling = (
      alertThresholdExceeded && 
      (costAmount >= budgetAmount || costAmount >= BUDGET_AMOUNT)
    );
    
    if (shouldDisableBilling) {
      console.log(`🚨 BUDGET EXCEEDED! Cost: $${costAmount}, Budget: $${budgetAmount}`);
      console.log('Disabling billing for project:', PROJECT_ID);
      
      await disableBilling(PROJECT_ID);
      
      console.log('✅ Billing successfully disabled');
      
      // Log the action for review
      await logBillingAction({
        timestamp: new Date().toISOString(),
        action: 'BILLING_DISABLED',
        reason: 'BUDGET_EXCEEDED',
        projectId: PROJECT_ID,
        costAmount,
        budgetAmount,
        budgetData
      });
      
    } else {
      console.log(`Budget alert received but threshold not met for shutdown. Cost: $${costAmount}, Budget: $${budgetAmount}`);
    }
    
  } catch (error) {
    console.error('Error processing budget alert:', error);
    
    // Log the error for review
    await logBillingAction({
      timestamp: new Date().toISOString(),
      action: 'ERROR',
      reason: 'PROCESSING_FAILED',
      projectId: PROJECT_ID,
      error: error.message,
      stack: error.stack
    });
    
    throw error;
  }
});

/**
 * Disables billing for the specified project
 */
async function disableBilling(projectId) {
  try {
    // Get OAuth2 client
    const auth = new google.auth.GoogleAuth({
      scopes: ['https://www.googleapis.com/auth/cloud-billing']
    });
    
    const authClient = await auth.getClient();
    
    // Get current billing info
    const billingInfoResponse = await cloudbilling.projects.getBillingInfo({
      auth: authClient,
      name: `projects/${projectId}`,
    });
    
    const currentBillingAccount = billingInfoResponse.data.billingAccountName;
    console.log('Current billing account:', currentBillingAccount);
    
    if (!currentBillingAccount) {
      console.log('Billing is already disabled for this project');
      return;
    }
    
    // Disable billing by setting billingAccountName to empty string
    const response = await cloudbilling.projects.updateBillingInfo({
      auth: authClient,
      name: `projects/${projectId}`,
      requestBody: {
        billingAccountName: '', // This disables billing
      },
    });
    
    console.log('Billing disabled successfully:', response.data);
    return response.data;
    
  } catch (error) {
    console.error('Error disabling billing:', error);
    throw new Error(`Failed to disable billing: ${error.message}`);
  }
}

/**
 * Logs billing actions to Cloud Logging for audit trail
 */
async function logBillingAction(actionData) {
  try {
    // Enhanced logging with structured data
    console.log('BILLING_ACTION_LOG:', JSON.stringify(actionData, null, 2));
    
    // In a production setup, you could also send this to:
    // - Cloud Firestore for persistent storage
    // - Email notifications
    // - Slack/Discord webhooks
    // - Cloud Monitoring for alerting
    
  } catch (error) {
    console.error('Error logging billing action:', error);
  }
}

// Export for testing
module.exports = {
  enforceBudget: functions.cloudEvent('enforceBudget'),
  disableBilling,
  logBillingAction
};