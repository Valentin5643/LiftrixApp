package com.example.liftrix.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.example.liftrix.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manager for Google Play Billing client operations.
 * Handles connection lifecycle, product queries, purchase flows, and validation.
 * Follows Clean Architecture principles with proper error handling and logging.
 */
@Singleton
class BillingClientManager @Inject constructor(
    private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        private const val PREMIUM_MONTHLY_PRODUCT_ID = "premium_monthly"
        private const val PREMIUM_YEARLY_PRODUCT_ID = "premium_yearly"
        private const val PRO_MONTHLY_PRODUCT_ID = "pro_monthly"
        private const val PRO_YEARLY_PRODUCT_ID = "pro_yearly"
        
        private val SUBSCRIPTION_PRODUCT_IDS = listOf(
            PREMIUM_MONTHLY_PRODUCT_ID,
            PREMIUM_YEARLY_PRODUCT_ID,
            PRO_MONTHLY_PRODUCT_ID,
            PRO_YEARLY_PRODUCT_ID
        )
    }

    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    /**
     * Start connection to Google Play Billing service
     */
    suspend fun startConnection(): BillingResult = suspendCancellableCoroutine { continuation ->
        if (billingClient.isReady) {
            _connectionState.value = BillingConnectionState.CONNECTED
            continuation.resume(BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build())
            return@suspendCancellableCoroutine
        }

        _connectionState.value = BillingConnectionState.CONNECTING
        
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = BillingConnectionState.CONNECTED
                    Timber.d("Google Play Billing client connected successfully")
                } else {
                    _connectionState.value = BillingConnectionState.DISCONNECTED
                    Timber.e("Google Play Billing setup failed: ${billingResult.debugMessage}")
                }
                continuation.resume(billingResult)
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingConnectionState.DISCONNECTED
                Timber.w("Google Play Billing service disconnected")
            }
        })
    }

    /**
     * Query subscription product details
     */
    suspend fun querySubscriptionDetails(): BillingResult = suspendCancellableCoroutine { continuation ->
        if (!billingClient.isReady) {
            val result = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .setDebugMessage("Billing client not ready")
                .build()
            continuation.resume(result)
            return@suspendCancellableCoroutine
        }

        val productList = SUBSCRIPTION_PRODUCT_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.d("Queried ${productDetailsList.size} subscription products")
                // Store product details for later use
                _productDetails.value = productDetailsList
            } else {
                Timber.e("Failed to query product details: ${billingResult.debugMessage}")
            }
            continuation.resume(billingResult)
        }
    }

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails.asStateFlow()

    /**
     * Launch billing flow for subscription purchase
     */
    suspend fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String
    ): BillingResult = suspendCancellableCoroutine { continuation ->
        if (!billingClient.isReady) {
            val result = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .setDebugMessage("Billing client not ready")
                .build()
            continuation.resume(result)
            return@suspendCancellableCoroutine
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        Timber.d("Launched billing flow for product: ${productDetails.productId}")
        continuation.resume(billingResult)
    }

    /**
     * Acknowledge purchase
     */
    suspend fun acknowledgePurchase(purchaseToken: String): BillingResult = suspendCancellableCoroutine { continuation ->
        if (!billingClient.isReady) {
            val result = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .setDebugMessage("Billing client not ready")
                .build()
            continuation.resume(result)
            return@suspendCancellableCoroutine
        }

        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.d("Purchase acknowledged successfully")
            } else {
                Timber.e("Failed to acknowledge purchase: ${billingResult.debugMessage}")
            }
            continuation.resume(billingResult)
        }
    }

    /**
     * Query existing purchases
     */
    suspend fun queryPurchases(): BillingResult = suspendCancellableCoroutine { continuation ->
        if (!billingClient.isReady) {
            val result = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .setDebugMessage("Billing client not ready")
                .build()
            continuation.resume(result)
            return@suspendCancellableCoroutine
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _purchases.value = purchasesList
                Timber.d("Queried ${purchasesList.size} existing purchases")
            } else {
                Timber.e("Failed to query purchases: ${billingResult.debugMessage}")
            }
            continuation.resume(billingResult)
        }
    }

    /**
     * Handle purchase updates from Google Play Billing
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            _purchases.value = purchases
            Timber.d("Purchases updated: ${purchases.size} purchases")
            
            // Process each purchase
            purchases.forEach { purchase ->
                processPurchase(purchase)
            }
        } else {
            Timber.e("Purchase update failed: ${billingResult.debugMessage}")
        }
    }

    /**
     * Process individual purchase
     */
    private fun processPurchase(purchase: Purchase) {
        Timber.d("Processing purchase: ${purchase.products.joinToString()}")
        
        // Verify purchase state
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Acknowledge purchase if not already acknowledged
            if (!purchase.isAcknowledged) {
                Timber.d("Purchase needs acknowledgment: ${purchase.purchaseToken}")
                // Acknowledgment will be handled by the repository layer
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Timber.d("Purchase is pending: ${purchase.purchaseToken}")
        }
    }

    /**
     * Get product details for a specific product ID
     */
    fun getProductDetails(productId: String): ProductDetails? {
        return _productDetails.value.find { it.productId == productId }
    }

    /**
     * Get subscription tier from product ID
     */
    fun getSubscriptionTier(productId: String): SubscriptionTier {
        return when (productId) {
            PREMIUM_MONTHLY_PRODUCT_ID, PREMIUM_YEARLY_PRODUCT_ID -> SubscriptionTier.PREMIUM
            PRO_MONTHLY_PRODUCT_ID, PRO_YEARLY_PRODUCT_ID -> SubscriptionTier.PRO
            else -> SubscriptionTier.FREE
        }
    }

    /**
     * Check if billing client is ready
     */
    val isReady: Boolean
        get() = billingClient.isReady

    /**
     * End connection to Google Play Billing service
     */
    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
            _connectionState.value = BillingConnectionState.DISCONNECTED
            Timber.d("Google Play Billing client disconnected")
        }
    }
}

/**
 * Enum representing billing connection states
 */
enum class BillingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}