package app.pwhs.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

enum class ProProductType {
    YEARLY,
    LIFETIME
}

sealed interface BillingPurchaseResult {
    data object Success : BillingPurchaseResult
    data class UserCanceled(val message: String? = null) : BillingPurchaseResult
    data class Error(val code: Int, val message: String) : BillingPurchaseResult
    data object Pending : BillingPurchaseResult
    data object AlreadyOwned : BillingPurchaseResult
}

interface BillingManager {
    val isProUser: StateFlow<Boolean>
    val yearlyPrice: StateFlow<String?>
    val lifetimePrice: StateFlow<String?>
    val isAvailable: Boolean

    fun startConnection()
    fun launchBillingFlow(
        activity: Activity,
        productType: ProProductType,
        onResult: ((BillingPurchaseResult) -> Unit)? = null,
    )
    fun queryPurchases(onComplete: ((Boolean) -> Unit)? = null)
    fun destroy()
}
