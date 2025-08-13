/**
 * Test script to simulate a budget alert and verify billing shutdown functionality
 */

const { PubSub } = require('@google-cloud/pubsub');

// Initialize Pub/Sub client
const pubsub = new PubSub();
const topicName = 'budget-alerts';
const topic = pubsub.topic(topicName);

// Simulate a budget alert payload that would trigger billing shutdown
const testBudgetAlert = {
  budgetDisplayName: 'Firebase Project liftrix-390cf - Automatic Billing Shutdown',
  alertThresholdExceeded: 1.0, // 100% of budget exceeded
  costAmount: 55.0, // $55 - exceeds our $50 budget
  budgetAmount: 50.0, // Our $50 budget
  costIntervalStart: '2025-08-01T00:00:00Z',
  costIntervalEnd: '2025-08-31T23:59:59Z',
  currencyCode: 'USD',
  budgetAmountType: 'SPECIFIED_AMOUNT',
  schemaVersion: '1.0'
};

async function testBillingShutdown() {
  try {
    console.log('🧪 Testing Budget Alert System');
    console.log('====================================');
    
    console.log('📊 Simulated Alert Data:', JSON.stringify(testBudgetAlert, null, 2));
    
    // Publish the test message to Pub/Sub topic
    const messageData = Buffer.from(JSON.stringify(testBudgetAlert));
    
    console.log('\n📤 Publishing test budget alert to Pub/Sub topic...');
    const messageId = await topic.publishMessage({
      data: messageData,
      attributes: {
        'test': 'true',
        'source': 'budget-test-script'
      }
    });
    
    console.log(`✅ Test message published successfully with ID: ${messageId}`);
    console.log('\n🔍 Expected behavior:');
    console.log('  1. Cloud Function should receive the message');
    console.log('  2. Function should detect cost ($55) > budget ($50)');
    console.log('  3. Function should disable billing for the project');
    console.log('  4. Check Cloud Function logs for execution details');
    
    console.log('\n📋 Next steps:');
    console.log('  1. Check Cloud Function logs:');
    console.log('     gcloud functions logs read enforceBudget --region=us-central1 --limit=50');
    console.log('  2. Verify billing status:');
    console.log('     gcloud billing projects describe liftrix-390cf');
    console.log('  3. If billing was disabled, re-enable manually using re-enable script');
    
  } catch (error) {
    console.error('❌ Error testing budget alert:', error);
    process.exit(1);
  }
}

// Safety check - only run in test mode
if (process.argv.includes('--run-test')) {
  testBillingShutdown();
} else {
  console.log('⚠️  SAFETY CHECK: This script will test the automatic billing shutdown system.');
  console.log('    This may actually disable billing for your project if the Cloud Function is working correctly.');
  console.log('    To proceed with the test, run: node test-budget-alert.js --run-test');
}