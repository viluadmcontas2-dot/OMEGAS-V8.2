package com.omegas.prohub.usb

/** Identidade USB observada na interface OMEGAS/MP48 usada pelo produto. */
object OmegasUsbIdentity {
    const val VENDOR_ID: Int = 0x10C4
    const val PRODUCT_ID: Int = 0xEA60

    fun matches(vendorId: Int, productId: Int): Boolean =
        vendorId == VENDOR_ID && productId == PRODUCT_ID
}
