package com.example.swipeclean.zen.billing

import com.android.billingclient.api.ProductDetails

/**
 * Extension function para verificar si la lista de ProductDetails está vacía.
 * Necesaria porque el tipo de retorno de queryProductDetailsAsync no expone
 * los métodos estándar de colecciones de Kotlin.
 */
fun List<ProductDetails>?.isNotEmptyCompat(): Boolean {
    return this != null && this.isNotEmpty()
}

/**
 * Extension function para obtener el primer elemento de forma segura.
 */
fun List<ProductDetails>?.firstOrNullCompat(): ProductDetails? = this?.firstOrNull()
