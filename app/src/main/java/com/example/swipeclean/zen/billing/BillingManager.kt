package com.example.swipeclean.zen.billing

import android.app.Activity
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingManager(private val activity: Activity) {

    private val TAG = "BillingManager"

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Unknown)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled purchase")
            _purchaseState.value = PurchaseState.Unknown
        } else {
            Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
            _purchaseState.value = PurchaseState.Error(billingResult.debugMessage)
        }
    }

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing setup finished successfully")
                    checkPremiumStatus()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected, will retry")
                // Si quieres, añade aquí un retry con backoff.
            }
        })
    }

    private fun checkPremiumStatus() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                var premium = false
                for (purchase in purchases) {
                    if (purchase.products.contains(PRODUCT_ID_ZEN_MODE) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        premium = true
                        break
                    }
                }
                _isPremium.value = premium
                Log.d(TAG, "Premium status: ${_isPremium.value}")
            } else {
                Log.e(TAG, "Failed to query purchases: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchPurchaseFlow() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_ZEN_MODE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, details ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Normaliza el tipo aunque venga de plataforma Java
                val list = (details as? List<*>)?.filterIsInstance<ProductDetails>().orEmpty()
                val productDetails: ProductDetails? = list.firstOrNull()

                if (productDetails != null) {
                    val productDetailsParamsList = listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )

                    val flowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .build()

                    _purchaseState.value = PurchaseState.Purchasing
                    billingClient.launchBillingFlow(activity, flowParams)
                } else {
                    Log.e(TAG, "No product details found")
                    _purchaseState.value = PurchaseState.Error("Product not found")
                }
            } else {
                Log.e(TAG, "Failed to query product details: ${billingResult.debugMessage}")
                _purchaseState.value = PurchaseState.Error(billingResult.debugMessage)
            }
        }

    }


    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase acknowledged successfully")
                        _isPremium.value = true
                        _purchaseState.value = PurchaseState.Purchased
                    } else {
                        Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                    }
                }
            } else {
                _isPremium.value = true
                _purchaseState.value = PurchaseState.Purchased
            }
        }
    }

    fun disconnect() {
        billingClient.endConnection()
    }

    companion object {
        const val PRODUCT_ID_ZEN_MODE = "premium_zen_mode"
    }
}

sealed class PurchaseState {
    object Unknown : PurchaseState()
    object Purchasing : PurchaseState()
    object Purchased : PurchaseState()
    data class Error(val message: String) : PurchaseState()
}
