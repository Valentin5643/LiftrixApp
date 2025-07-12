package com.example.liftrix.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.example.liftrix.domain.model.SubscriptionProvider
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BillingRepositoryImpl
 * Tests billing repository operations, purchase validation, and domain model conversion
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BillingRepositoryTest {

    private lateinit var mockBillingClientManager: BillingClientManager
    private lateinit var billingRepository: BillingRepositoryImpl

    @Before
    fun setUp() {
        mockBillingClientManager = mockk(relaxed = true)
        billingRepository = BillingRepositoryImpl(mockBillingClientManager)
    }

    @Test
    fun `when initialize succeeds, should return success`() = runTest {
        // Given
        val successResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()
        
        coEvery { mockBillingClientManager.startConnection() } returns successResult
        
        // When
        val result = billingRepository.initialize()
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { mockBillingClientManager.startConnection() }
    }

    @Test
    fun `when initialize fails, should return failure`() = runTest {
        // Given
        val failureResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.ERROR)
            .setDebugMessage("Test error")
            .build()
        
        coEvery { mockBillingClientManager.startConnection() } returns failureResult
        
        // When
        val result = billingRepository.initialize()
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Billing initialization failed: Test error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `when querySubscriptionProducts succeeds, should return products`() = runTest {
        // Given
        val successResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()
        
        every { mockBillingClientManager.productDetails } returns MutableStateFlow(emptyList<ProductDetails>())
        coEvery { mockBillingClientManager.querySubscriptionDetails() } returns successResult
        
        // When
        val result = billingRepository.querySubscriptionProducts()
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
        coVerify { mockBillingClientManager.querySubscriptionDetails() }
    }

    @Test
    fun `when querySubscriptionProducts fails, should return failure`() = runTest {
        // Given
        val failureResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.ERROR)
            .setDebugMessage("Product query failed")
            .build()
        
        coEvery { mockBillingClientManager.querySubscriptionDetails() } returns failureResult
        
        // When
        val result = billingRepository.querySubscriptionProducts()
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to query products") == true)
    }

    @Test
    fun `when launchSubscriptionPurchase with valid product, should succeed`() = runTest {
        // Given
        val activity = mockk<Activity>(relaxed = true)
        val productId = "premium_monthly"
        val offerToken = "offer_token_123"
        val productDetails = mockk<com.android.billingclient.api.ProductDetails>(relaxed = true)
        
        val successResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()
        
        every { mockBillingClientManager.getProductDetails(productId) } returns productDetails
        coEvery { mockBillingClientManager.launchBillingFlow(activity, productDetails, offerToken) } returns successResult
        
        // When
        val result = billingRepository.launchSubscriptionPurchase(activity, productId, offerToken)
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { mockBillingClientManager.launchBillingFlow(activity, productDetails, offerToken) }
    }

    @Test
    fun `when launchSubscriptionPurchase with invalid product, should fail`() = runTest {
        // Given
        val activity = mockk<Activity>(relaxed = true)
        val productId = "invalid_product"
        val offerToken = "offer_token_123"
        
        every { mockBillingClientManager.getProductDetails(productId) } returns null
        
        // When
        val result = billingRepository.launchSubscriptionPurchase(activity, productId, offerToken)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Product details not found") == true)
    }

    @Test
    fun `when acknowledgePurchase succeeds, should return success`() = runTest {
        // Given
        val purchaseToken = "purchase_token_123"
        val successResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()
        
        coEvery { mockBillingClientManager.acknowledgePurchase(purchaseToken) } returns successResult
        
        // When
        val result = billingRepository.acknowledgePurchase(purchaseToken)
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { mockBillingClientManager.acknowledgePurchase(purchaseToken) }
    }

    @Test
    fun `when acknowledgePurchase fails, should return failure`() = runTest {
        // Given
        val purchaseToken = "purchase_token_123"
        val failureResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.ERROR)
            .setDebugMessage("Acknowledgment failed")
            .build()
        
        coEvery { mockBillingClientManager.acknowledgePurchase(purchaseToken) } returns failureResult
        
        // When
        val result = billingRepository.acknowledgePurchase(purchaseToken)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to acknowledge purchase") == true)
    }

    @Test
    fun `when convertPurchaseToSubscription with valid purchase, should return subscription`() {
        // Given
        val userId = "user_123"
        val mockPurchase = mockk<Purchase>(relaxed = true)
        
        every { mockPurchase.purchaseState } returns Purchase.PurchaseState.PURCHASED
        every { mockPurchase.products } returns listOf("premium_monthly")
        every { mockPurchase.purchaseToken } returns "token_123"
        every { mockPurchase.purchaseTime } returns System.currentTimeMillis()
        every { mockPurchase.isAcknowledged } returns true
        every { mockPurchase.isAutoRenewing } returns true
        
        every { mockBillingClientManager.getSubscriptionTier("premium_monthly") } returns SubscriptionTier.PREMIUM
        
        // When
        val subscription = billingRepository.convertPurchaseToSubscription(mockPurchase, userId)
        
        // Then
        assertNotNull(subscription)
        assertEquals(userId, subscription?.userId)
        assertEquals(SubscriptionTier.PREMIUM, subscription?.tier)
        assertEquals(SubscriptionStatus.ACTIVE, subscription?.status)
        assertEquals(SubscriptionProvider.GOOGLE_PLAY, subscription?.provider)
        assertEquals("premium_monthly", subscription?.productId)
        assertEquals("token_123", subscription?.subscriptionId)
        assertEquals(true, subscription?.autoRenew)
    }

    @Test
    fun `when convertPurchaseToSubscription with invalid purchase state, should return null`() {
        // Given
        val userId = "user_123"
        val mockPurchase = mockk<Purchase>(relaxed = true)
        
        every { mockPurchase.purchaseState } returns Purchase.PurchaseState.PENDING
        
        // When
        val subscription = billingRepository.convertPurchaseToSubscription(mockPurchase, userId)
        
        // Then
        assertNull(subscription)
    }

    @Test
    fun `when validatePurchase with valid purchase, should return true`() {
        // Given
        val mockPurchase = mockk<Purchase>(relaxed = true)
        
        every { mockPurchase.purchaseToken } returns "valid_token"
        every { mockPurchase.products } returns listOf("premium_monthly")
        every { mockPurchase.purchaseState } returns Purchase.PurchaseState.PURCHASED
        
        // When
        val isValid = billingRepository.validatePurchase(mockPurchase)
        
        // Then
        assertTrue(isValid)
    }

    @Test
    fun `when validatePurchase with empty purchase token, should return false`() {
        // Given
        val mockPurchase = mockk<Purchase>(relaxed = true)
        
        every { mockPurchase.purchaseToken } returns ""
        every { mockPurchase.products } returns listOf("premium_monthly")
        every { mockPurchase.purchaseState } returns Purchase.PurchaseState.PURCHASED
        
        // When
        val isValid = billingRepository.validatePurchase(mockPurchase)
        
        // Then
        assertFalse(isValid)
    }

    @Test
    fun `when validatePurchase with empty products, should return false`() {
        // Given
        val mockPurchase = mockk<Purchase>(relaxed = true)
        
        every { mockPurchase.purchaseToken } returns "valid_token"
        every { mockPurchase.products } returns emptyList()
        every { mockPurchase.purchaseState } returns Purchase.PurchaseState.PURCHASED
        
        // When
        val isValid = billingRepository.validatePurchase(mockPurchase)
        
        // Then
        assertFalse(isValid)
    }

    @Test
    fun `when validatePurchase with unspecified state, should return false`() {
        // Given
        val mockPurchase = mockk<Purchase>(relaxed = true)
        
        every { mockPurchase.purchaseToken } returns "valid_token"
        every { mockPurchase.products } returns listOf("premium_monthly")
        every { mockPurchase.purchaseState } returns Purchase.PurchaseState.UNSPECIFIED_STATE
        
        // When
        val isValid = billingRepository.validatePurchase(mockPurchase)
        
        // Then
        assertFalse(isValid)
    }

    @Test
    fun `when cleanup called, should end connection`() {
        // When
        billingRepository.cleanup()
        
        // Then
        verify { mockBillingClientManager.endConnection() }
    }
}