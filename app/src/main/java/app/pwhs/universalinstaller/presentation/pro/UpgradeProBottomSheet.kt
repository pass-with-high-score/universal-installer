package app.pwhs.universalinstaller.presentation.pro

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.core.R
import app.pwhs.core.billing.BillingManager
import app.pwhs.core.billing.BillingPurchaseResult
import app.pwhs.core.billing.ProProductType
import app.pwhs.core.util.openUriSafely
import app.pwhs.core.telemetry.AnalyticsHelper
import app.pwhs.universalinstaller.presentation.pro.components.PaywallContent
import app.pwhs.universalinstaller.presentation.pro.components.ProCelebrationContent
import app.pwhs.universalinstaller.presentation.pro.components.ProIssueDialog
import app.pwhs.universalinstaller.presentation.pro.components.ProSupporterView
import app.pwhs.universalinstaller.presentation.setting.virustotal.VirusTotalSettingActivity

private const val SUPPORT_COMMUNITY_URL = "https://t.me/blockads_android"
private const val GOOGLE_PLAY_SUBS_URL = "https://play.google.com/store/account/subscriptions"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeProBottomSheet(
    billingManager: BillingManager,
    onDismissRequest: () -> Unit,
    source: String = "unknown",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val activity = context as? Activity

    val isPro by billingManager.isProUser.collectAsState()
    val yearlyPrice by billingManager.yearlyPrice.collectAsState()
    val lifetimePrice by billingManager.lifetimePrice.collectAsState()

    var justPurchased by remember { mutableStateOf(false) }
    var selectedPlan by remember { mutableStateOf(ProProductType.LIFETIME) }
    var isProcessing by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var activeIssueResult by remember { mutableStateOf<BillingPurchaseResult?>(null) }

    val restoreSuccessMsg = stringResource(R.string.pro_restore_success)
    val restoreNotFoundMsg = stringResource(R.string.pro_restore_not_found)

    LaunchedEffect(Unit) {
        billingManager.startConnection()
        AnalyticsHelper.logPaywallViewed(source)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Crossfade(
            targetState = when {
                justPurchased -> SheetContentState.CELEBRATION
                isPro -> SheetContentState.ALREADY_PRO
                else -> SheetContentState.PAYWALL
            },
            label = "PaywallStateTransition",
        ) { state ->
            when (state) {
                SheetContentState.CELEBRATION -> {
                    ProCelebrationContent(onDismiss = onDismissRequest)
                }

                SheetContentState.ALREADY_PRO -> {
                    ProSupporterView(
                        onManageSubscriptions = { uriHandler.openUriSafely(context, GOOGLE_PLAY_SUBS_URL) },
                        onDismiss = onDismissRequest,
                    )
                }

                SheetContentState.PAYWALL -> {
                    PaywallContent(
                        billingManager = billingManager,
                        yearlyPrice = yearlyPrice,
                        lifetimePrice = lifetimePrice,
                        selectedPlan = selectedPlan,
                        isProcessing = isProcessing,
                        isRestoring = isRestoring,
                        onPlanSelected = {
                            selectedPlan = it
                            AnalyticsHelper.logPaywallPlanSelected(it.name.lowercase())
                        },
                        onPurchase = {
                            activity?.let { act ->
                                AnalyticsHelper.logCheckoutStarted(selectedPlan.name.lowercase(), source)
                                isProcessing = true
                                billingManager.launchBillingFlow(act, selectedPlan) { result ->
                                    isProcessing = false
                                    when (result) {
                                        is BillingPurchaseResult.Success -> {
                                            justPurchased = true
                                            val value = if (selectedPlan == ProProductType.YEARLY) 1.99 else 4.99
                                            AnalyticsHelper.logInAppPurchase(selectedPlan.name.lowercase(), value)
                                        }
                                        is BillingPurchaseResult.AlreadyOwned -> {
                                            justPurchased = true
                                            val value = if (selectedPlan == ProProductType.YEARLY) 1.99 else 4.99
                                            AnalyticsHelper.logInAppPurchase(selectedPlan.name.lowercase(), value)
                                        }
                                        is BillingPurchaseResult.UserCanceled -> {
                                            AnalyticsHelper.logPurchaseFailed(selectedPlan.name.lowercase(), -1, "USER_CANCELED")
                                            activeIssueResult = result
                                        }
                                        is BillingPurchaseResult.Error -> {
                                            AnalyticsHelper.logPurchaseFailed(selectedPlan.name.lowercase(), result.code, result.message)
                                            activeIssueResult = result
                                        }
                                        else -> {
                                            activeIssueResult = result
                                        }
                                    }
                                }
                            }
                        },
                        onRestore = {
                            isRestoring = true
                            billingManager.queryPurchases { hasPro ->
                                isRestoring = false
                                AnalyticsHelper.logPurchaseRestored(hasPro)
                                if (hasPro) {
                                    justPurchased = true
                                    Toast.makeText(context.applicationContext, restoreSuccessMsg, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context.applicationContext, restoreNotFoundMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    activeIssueResult?.let { issue ->
        ProIssueDialog(
            result = issue,
            onDismissRequest = { activeIssueResult = null },
            onRetry = {
                activeIssueResult = null
                activity?.let { act ->
                    AnalyticsHelper.logCheckoutStarted(selectedPlan.name.lowercase(), source)
                    isProcessing = true
                    billingManager.launchBillingFlow(act, selectedPlan) { result ->
                        isProcessing = false
                        when (result) {
                            is BillingPurchaseResult.Success -> {
                                justPurchased = true
                                val value = if (selectedPlan == ProProductType.YEARLY) 1.99 else 4.99
                                AnalyticsHelper.logInAppPurchase(selectedPlan.name.lowercase(), value)
                            }
                            is BillingPurchaseResult.AlreadyOwned -> {
                                justPurchased = true
                                val value = if (selectedPlan == ProProductType.YEARLY) 1.99 else 4.99
                                AnalyticsHelper.logInAppPurchase(selectedPlan.name.lowercase(), value)
                            }
                            is BillingPurchaseResult.UserCanceled -> {
                                AnalyticsHelper.logPurchaseFailed(selectedPlan.name.lowercase(), -1, "USER_CANCELED")
                                activeIssueResult = result
                            }
                            is BillingPurchaseResult.Error -> {
                                AnalyticsHelper.logPurchaseFailed(selectedPlan.name.lowercase(), result.code, result.message)
                                activeIssueResult = result
                            }
                            else -> {
                                activeIssueResult = result
                            }
                        }
                    }
                }
            },
            onUsePersonalKey = {
                activeIssueResult = null
                onDismissRequest()
                context.startActivity(Intent(context, VirusTotalSettingActivity::class.java))
            },
            onContactSupport = {
                activeIssueResult = null
                uriHandler.openUriSafely(context, SUPPORT_COMMUNITY_URL)
            },
        )
    }
}

private enum class SheetContentState {
    PAYWALL,
    CELEBRATION,
    ALREADY_PRO,
}
