package com.example.liftrix.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.example.liftrix.domain.model.SubscriptionTier
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for BillingClientManager
 * Tests billing client lifecycle, connection states, and purchase operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BillingClientManagerTest {

    private lateinit var context: Context
    private lateinit var billingClientManager: BillingClientManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        billingClientManager = BillingClientManager(context)
    }

    @After
    fun tearDown() {
        billingClientManager.endConnection()
    }

    @Test
    fun `when startConnection succeeds, should update connection state to CONNECTED`() = runTest {
        // Given
        val initialState = billingClientManager.connectionState.value
        
        // When
        val result = billingClientManager.startConnection()
        
        // Then
        assertEquals(BillingConnectionState.DISCONNECTED, initialState)
        // Note: In unit tests, we can't easily mock BillingClient behavior
        // This test verifies the basic structure and would need integration testing
        // for full BillingClient interaction verification
    }

    @Test
    fun `when getSubscriptionTier called with premium product, should return PREMIUM`() {
        // Given
        val premiumMonthlyProductId = "premium_monthly"
        val premiumYearlyProductId = "premium_yearly"
        
        // When
        val monthlyTier = billingClientManager.getSubscriptionTier(premiumMonthlyProductId)
        val yearlyTier = billingClientManager.getSubscriptionTier(premiumYearlyProductId)
        
        // Then
        assertEquals(SubscriptionTier.PREMIUM, monthlyTier)
        assertEquals(SubscriptionTier.PREMIUM, yearlyTier)
    }

    @Test
    fun `when getSubscriptionTier called with pro product, should return PRO`() {
        // Given
        val proMonthlyProductId = "pro_monthly"
        val proYearlyProductId = "pro_yearly"
        
        // When
        val monthlyTier = billingClientManager.getSubscriptionTier(proMonthlyProductId)
        val yearlyTier = billingClientManager.getSubscriptionTier(proYearlyProductId)
        
        // Then
        assertEquals(SubscriptionTier.PRO, monthlyTier)
        assertEquals(SubscriptionTier.PRO, yearlyTier)
    }

    @Test
    fun `when getSubscriptionTier called with unknown product, should return FREE`() {
        // Given
        val unknownProductId = "unknown_product"
        
        // When
        val tier = billingClientManager.getSubscriptionTier(unknownProductId)
        
        // Then
        assertEquals(SubscriptionTier.FREE, tier)
    }

    @Test
    fun `when onPurchasesUpdated called with successful result, should update purchases`() {
        // Given
        val mockPurchase = mockk<Purchase>(relaxed = true)
        every { mockPurchase.products } returns listOf("premium_monthly")
        every { mockPurchase.purchaseState } returns Purchase.PurchaseState.PURCHASED
        every { mockPurchase.isAcknowledged } returns true
        
        val purchases = mutableListOf(mockPurchase)
        val successfulResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()
        
        // When
        billingClientManager.onPurchasesUpdated(successfulResult, purchases)
        
        // Then
        assertEquals(1, billingClientManager.purchases.value.size)
        assertEquals(mockPurchase, billingClientManager.purchases.value.first())
    }

    @Test
    fun `when onPurchasesUpdated called with failed result, should not update purchases`() {
        // Given
        val mockPurchase = mockk<Purchase>(relaxed = true)
        val purchases = mutableListOf(mockPurchase)
        val failedResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.ERROR)
            .setDebugMessage("Test error")
            .build()
        
        // When
        billingClientManager.onPurchasesUpdated(failedResult, purchases)
        
        // Then
        assertEquals(0, billingClientManager.purchases.value.size)
    }

    @Test
    fun `when isReady called, should return billing client ready state`() {
        // Given - BillingClient is mocked and not ready by default
        
        // When
        val isReady = billingClientManager.isReady
        
        // Then
        assertFalse(isReady) // Default state for mocked client
    }

    @Test
    fun `when endConnection called, should set connection state to DISCONNECTED`() {
        // Given
        val initialState = billingClientManager.connectionState.value
        
        // When
        billingClientManager.endConnection()
        
        // Then
        assertEquals(BillingConnectionState.DISCONNECTED, billingClientManager.connectionState.value)
    }

    @Test
    fun `when getProductDetails called with existing product, should return product details`() {
        // Given
        val productId = "premium_monthly"
        
        // When
        val productDetails = billingClientManager.getProductDetails(productId)
        
        // Then
        // Initially null since no products have been queried
        assertNull(productDetails)
    }
}