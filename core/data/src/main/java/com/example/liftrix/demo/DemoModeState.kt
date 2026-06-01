package com.example.liftrix.demo

data class DemoModeState(
    val enabled: Boolean = false,
    val sessionSeed: Long? = null,
    val activatedAtMillis: Long? = null,
    val lastDisabledAtMillis: Long? = null
) {
    val isActive: Boolean get() = enabled && sessionSeed != null

    companion object {
        val Inactive = DemoModeState()
    }
}
