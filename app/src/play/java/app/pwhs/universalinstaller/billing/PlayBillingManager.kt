package app.pwhs.universalinstaller.billing

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.edit
import app.pwhs.core.billing.BillingManager
import app.pwhs.core.billing.ProProductType
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

fun createBillingManager(context: Context): BillingManager = PlayBillingManager(context)

private class PlayBillingManager(
    private val context: Context,
) : BillingManager, PurchasesUpdatedListener, BillingClientStateListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isProUser = MutableStateFlow(false)
    override val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()

    private val _yearlyPrice = MutableStateFlow<String?>(null)
    override val yearlyPrice: StateFlow<String?> = _yearlyPrice.asStateFlow()

    private val _lifetimePrice = MutableStateFlow<String?>(null)
    override val lifetimePrice: StateFlow<String?> = _lifetimePrice.asStateFlow()

    override val isAvailable: Boolean = true

    private var subscriptionDetails: ProductDetails? = null
    private var lifetimeDetails: ProductDetails? = null

    private var pendingPurchaseCallback: ((Boolean) -> Unit)? = null

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        scope.launch {
            context.dataStore.data.collectLatest { prefs ->
                _isProUser.value = prefs[SharedPrefsKeys.IS_PRO_USER] ?: false
            }
        }
    }

    override fun startConnection() {
        if (!billingClient.isReady) {
            billingClient.startConnection(this)
        } else {
            queryProductDetails()
            queryPurchases()
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Timber.i("PlayBillingManager: Connected successfully")
            queryProductDetails()
            queryPurchases()
        } else {
            Timber.w("PlayBillingManager: Connection failed with code ${billingResult.responseCode}: ${billingResult.debugMessage}")
        }
    }

    override fun onBillingServiceDisconnected() {
        Timber.w("PlayBillingManager: Billing service disconnected")
    }

    private fun queryProductDetails() {
        if (!billingClient.isReady) return

        // 1. Query Subscription
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUBSCRIPTION_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(subsParams) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = productDetailsResult.productDetailsList.firstOrNull()
                if (details != null) {
                    subscriptionDetails = details
                    val price = details.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice
                    _yearlyPrice.value = price
                    Timber.d("PlayBillingManager: Subscription price loaded: $price")
                }
            } else {
                Timber.w("PlayBillingManager: Failed to query subscriptions: ${result.debugMessage}")
            }
        }

        // 2. Query Lifetime (In-App)
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(LIFETIME_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = productDetailsResult.productDetailsList.firstOrNull()
                if (details != null) {
                    lifetimeDetails = details
                    val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                    _lifetimePrice.value = price
                    Timber.d("PlayBillingManager: Lifetime price loaded: $price")
                }
            } else {
                Timber.w("PlayBillingManager: Failed to query in-app products: ${result.debugMessage}")
            }
        }
    }

    override fun queryPurchases() {
        if (!billingClient.isReady) return

        // Check Subscriptions
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(subsParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }

        // Check In-App (Lifetime)
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(inAppParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    override fun launchBillingFlow(
        activity: Activity,
        productType: ProProductType,
        onResult: ((Boolean) -> Unit)?
    ) {
        pendingPurchaseCallback = onResult

        val details = when (productType) {
            ProProductType.YEARLY -> subscriptionDetails
            ProProductType.LIFETIME -> lifetimeDetails
        }

        if (details == null) {
            Timber.w("PlayBillingManager: Details for $productType not loaded yet")
            queryProductDetails()
            onResult?.invoke(false)
            return
        }

        val productDetailsParamsList = when (productType) {
            ProProductType.YEARLY -> {
                val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken.orEmpty()
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            }
            ProProductType.LIFETIME -> {
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            }
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val response = billingClient.launchBillingFlow(activity, flowParams)
        if (response.responseCode != BillingClient.BillingResponseCode.OK) {
            Timber.e("PlayBillingManager: launchBillingFlow failed: ${response.debugMessage}")
            onResult?.invoke(false)
            pendingPurchaseCallback = null
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
            pendingPurchaseCallback?.invoke(true)
        } else {
            Timber.w("PlayBillingManager: onPurchasesUpdated response=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
            pendingPurchaseCallback?.invoke(false)
        }
        pendingPurchaseCallback = null
    }

    private fun handlePurchases(purchases: List<Purchase>?) {
        if (purchases.isNullOrEmpty()) return

        var hasValidPro = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                val matchesProduct = purchase.products.contains(SUBSCRIPTION_PRODUCT_ID) ||
                        purchase.products.contains(LIFETIME_PRODUCT_ID)
                if (matchesProduct) {
                    hasValidPro = true
                    if (!purchase.isAcknowledged) {
                        acknowledge(purchase)
                    }
                }
            }
        }

        if (hasValidPro) {
            updateProStatus(true)
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            Timber.i("PlayBillingManager: Purchase acknowledged: ${result.responseCode}")
        }
    }

    private fun updateProStatus(isPro: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[SharedPrefsKeys.IS_PRO_USER] = isPro
            }
            _isProUser.value = isPro
        }
    }

    override fun destroy() {
        billingClient.endConnection()
        scope.cancel()
    }

    companion object {
        private const val SUBSCRIPTION_PRODUCT_ID = "universal_installer_pro_subscription"
        private const val LIFETIME_PRODUCT_ID = "universal_installer_pro_lifetime"
    }
}
