package app.pwhs.universalinstaller.billing

import android.app.Activity
import android.content.Context
import app.pwhs.core.billing.BillingManager
import app.pwhs.core.billing.BillingPurchaseResult
import app.pwhs.core.billing.ProProductType
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.network.VirusTotalQuotaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun createBillingManager(context: Context): BillingManager = NoOpBillingManager(context)

private class NoOpBillingManager(context: Context) : BillingManager {
    private val _isProUser = MutableStateFlow(false)
    override val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()
    override val yearlyPrice: StateFlow<String?> = MutableStateFlow(null)
    override val lifetimePrice: StateFlow<String?> = MutableStateFlow(null)
    override val isAvailable: Boolean = false

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            context.dataStore.data.collectLatest { prefs ->
                val isPurchasedPro = prefs[SharedPrefsKeys.IS_PRO_USER] ?: false
                val trialExp = prefs[SharedPrefsKeys.PRO_TRIAL_EXPIRATION_TIMESTAMP] ?: 0L
                val isTrial = trialExp > VirusTotalQuotaStore.currentTrueTimeMillis()
                _isProUser.value = isPurchasedPro || isTrial
            }
        }
    }

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
