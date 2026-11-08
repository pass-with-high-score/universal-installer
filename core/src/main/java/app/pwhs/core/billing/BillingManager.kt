package app.pwhs.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

enum class ProProductType {
    YEARLY,
    LIFETIME
}

interface BillingManager {
    val isProUser: StateFlow<Boolean>
    val yearlyPrice: StateFlow<String?>
    val lifetimePrice: StateFlow<String?>
    val isAvailable: Boolean

    fun startConnection()
    fun launchBillingFlow(activity: Activity, productType: ProProductType, onResult: ((Boolean) -> Unit)? = null)
    fun queryPurchases()
    fun destroy()
}
