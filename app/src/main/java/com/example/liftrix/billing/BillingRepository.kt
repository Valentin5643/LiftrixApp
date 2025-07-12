package com.example.liftrix.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.example.liftrix.domain.model.Subscription
import com.example.liftrix.domain.model.SubscriptionProvider
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for Google Play Billing operations.
 * Provides high-level abstraction over BillingClientManager with domain model integration.
 */
interface BillingRepository {
    
    /**
     * Get current connection state
     */
    val connectionState: Flow<BillingConnectionState>
    
    /**
     * Get available subscription products
     */
    val subscriptionProducts: Flow<List<SubscriptionProduct>>
    
    /**
     * Get current purchases
     */
    val purchases: Flow<List<Purchase>>
    
    /**
     * Initialize billing client connection
     */
    suspend fun initialize(): Result<Unit>
    
    /**
     * Query available subscription products
     */
    suspend fun querySubscriptionProducts(): Result<List<SubscriptionProduct>>
    
    /**
     * Launch purchase flow for subscription
     */
    suspend fun launchSubscriptionPurchase(
        activity: Activity,
        productId: String,
        offerToken: String
    ): Result<Unit>
    
    /**
     * Acknowledge purchase
     */
    suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit>
    
    /**
     * Query existing purchases
     */
    suspend fun queryPurchases(): Result<List<Purchase>>
    
    /**
     * Convert Google Play purchase to domain subscription
     */
    fun convertPurchaseToSubscription(purchase: Purchase, userId: String): Subscription?
    
    /**
     * Validate purchase security
     */
    fun validatePurchase(purchase: Purchase): Boolean
    
    /**
     * Clean up resources
     */
    fun cleanup()
}

/**
 * Implementation of BillingRepository using BillingClientManager
 */
@Singleton
class BillingRepositoryImpl @Inject constructor(
    private val billingClientManager: BillingClientManager
) : BillingRepository {

    override val connectionState: Flow<BillingConnectionState> = billingClientManager.connectionState

    override val subscriptionProducts: Flow<List<SubscriptionProduct>> = 
        billingClientManager.productDetails.map { productDetailsList ->
            productDetailsList.map { productDetails ->
                convertToSubscriptionProduct(productDetails)
            }
        }

    override val purchases: Flow<List<Purchase>> = billingClientManager.purchases

    override suspend fun initialize(): Result<Unit> {
        return try {
            val billingResult = billingClientManager.startConnection()
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.d("Billing repository initialized successfully")
                Result.success(Unit)
            } else {
                val error = Exception("Billing initialization failed: ${billingResult.debugMessage}")
                Timber.e(error, "Failed to initialize billing repository")
                Result.failure(error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during billing initialization")
            Result.failure(e)
        }
    }

    override suspend fun querySubscriptionProducts(): Result<List<SubscriptionProduct>> {
        return try {
            val billingResult = billingClientManager.querySubscriptionDetails()
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val products = billingClientManager.productDetails.value.map { productDetails ->
                    convertToSubscriptionProduct(productDetails)
                }
                Timber.d("Queried ${products.size} subscription products")
                Result.success(products)
            } else {
                val error = Exception("Failed to query products: ${billingResult.debugMessage}")
                Timber.e(error, "Failed to query subscription products")
                Result.failure(error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during product query")
            Result.failure(e)
        }
    }

    override suspend fun launchSubscriptionPurchase(
        activity: Activity,
        productId: String,
        offerToken: String
    ): Result<Unit> {
        return try {
            val productDetails = billingClientManager.getProductDetails(productId)
            if (productDetails == null) {
                val error = Exception("Product details not found for: $productId")
                Timber.e(error, "Product details missing for purchase")
                return Result.failure(error)
            }

            val billingResult = billingClientManager.launchBillingFlow(
                activity = activity,
                productDetails = productDetails,
                offerToken = offerToken
            )

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.d("Successfully launched billing flow for: $productId")
                Result.success(Unit)
            } else {
                val error = Exception("Failed to launch billing flow: ${billingResult.debugMessage}")
                Timber.e(error, "Billing flow launch failed")
                Result.failure(error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during billing flow launch")
            Result.failure(e)
        }
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit> {
        return try {
            val billingResult = billingClientManager.acknowledgePurchase(purchaseToken)
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.d("Purchase acknowledged successfully: $purchaseToken")
                Result.success(Unit)
            } else {
                val error = Exception("Failed to acknowledge purchase: ${billingResult.debugMessage}")
                Timber.e(error, "Purchase acknowledgment failed")
                Result.failure(error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during purchase acknowledgment")
            Result.failure(e)
        }
    }

    override suspend fun queryPurchases(): Result<List<Purchase>> {
        return try {
            val billingResult = billingClientManager.queryPurchases()
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchases = billingClientManager.purchases.value
                Timber.d("Queried ${purchases.size} purchases")
                Result.success(purchases)
            } else {
                val error = Exception("Failed to query purchases: ${billingResult.debugMessage}")
                Timber.e(error, "Purchase query failed")
                Result.failure(error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during purchase query")
            Result.failure(e)
        }
    }

    override fun convertPurchaseToSubscription(purchase: Purchase, userId: String): Subscription? {
        return try {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
                Timber.w("Purchase not in purchased state: ${purchase.purchaseState}")
                return null
            }

            val productId = purchase.products.firstOrNull()
            if (productId == null) {
                Timber.w("No product ID found in purchase")
                return null
            }

            val tier = billingClientManager.getSubscriptionTier(productId)
            val now = Instant.now()

            Subscription(
                userId = userId,
                tier = tier,
                status = if (purchase.isAcknowledged) SubscriptionStatus.ACTIVE else SubscriptionStatus.PENDING,
                provider = SubscriptionProvider.GOOGLE_PLAY,
                productId = productId,
                subscriptionId = purchase.purchaseToken,
                startedAt = Instant.ofEpochMilli(purchase.purchaseTime),
                expiresAt = null, // Will be updated from server verification
                autoRenew = purchase.isAutoRenewing,
                currency = "USD" // Default, should be updated from product details
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to convert purchase to subscription")
            null
        }
    }

    override fun validatePurchase(purchase: Purchase): Boolean {
        return try {
            // Basic validation checks
            when {
                purchase.purchaseToken.isBlank() -> {
                    Timber.w("Purchase token is blank")
                    false
                }
                purchase.products.isEmpty() -> {
                    Timber.w("No products in purchase")
                    false
                }
                purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                    Timber.w("Purchase state is unspecified")
                    false
                }
                else -> {
                    Timber.d("Purchase validation passed")
                    true
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during purchase validation")
            false
        }
    }

    override fun cleanup() {
        billingClientManager.endConnection()
        Timber.d("Billing repository cleaned up")
    }

    /**
     * Convert ProductDetails to SubscriptionProduct domain model
     */
    private fun convertToSubscriptionProduct(productDetails: ProductDetails): SubscriptionProduct {
        val tier = billingClientManager.getSubscriptionTier(productDetails.productId)
        val subscriptionOfferDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
        
        return SubscriptionProduct(
            productId = productDetails.productId,
            tier = tier,
            name = productDetails.name,
            description = productDetails.description,
            offerToken = subscriptionOfferDetails?.offerToken,
            pricingPhases = subscriptionOfferDetails?.pricingPhases?.pricingPhaseList?.map { phase ->
                PricingPhase(
                    price = phase.formattedPrice,
                    priceCurrencyCode = phase.priceCurrencyCode,
                    billingPeriod = phase.billingPeriod,
                    billingCycleCount = phase.billingCycleCount
                )
            } ?: emptyList()
        )
    }
}

/**
 * Domain model for subscription product
 */
data class SubscriptionProduct(
    val productId: String,
    val tier: SubscriptionTier,
    val name: String,
    val description: String,
    val offerToken: String?,
    val pricingPhases: List<PricingPhase>
) {
    val isValid: Boolean
        get() = productId.isNotBlank() && name.isNotBlank() && offerToken != null
        
    val formattedPrice: String?
        get() = pricingPhases.firstOrNull()?.price
}

/**
 * Domain model for pricing phase
 */
data class PricingPhase(
    val price: String,
    val priceCurrencyCode: String,
    val billingPeriod: String,
    val billingCycleCount: Int
)