package app.pwhs.universalinstaller.billing

import android.app.Activity
import android.content.Context
import app.pwhs.core.billing.BillingManager
import app.pwhs.core.billing.BillingPurchaseResult
import app.pwhs.core.billing.ProProductType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun createBillingManager(context: Context): BillingManager = NoOpBillingManager()

private class NoOpBillingManager : BillingManager {
    override val isProUser: StateFlow<Boolean> = MutableStateFlow(false)
    override val yearlyPrice: StateFlow<String?> = MutableStateFlow(null)
    override val lifetimePrice: StateFlow<String?> = MutableStateFlow(null)
    override val isAvailable: Boolean = false

    override fun startConnection() {}
    override fun launchBillingFlow(
        activity: Activity,
        productType: ProProductType,
        onResult: ((BillingPurchaseResult) -> Unit)?,
    ) {
        onResult?.invoke(BillingPurchaseResult.Error(-1, "Billing not supported in open-source build"))
    }
    override fun queryPurchases(onComplete: ((Boolean) -> Unit)?) {
        onComplete?.invoke(false)
    }
    override fun destroy() {}
}
