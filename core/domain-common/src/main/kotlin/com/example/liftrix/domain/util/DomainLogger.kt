package com.example.liftrix.domain.util

object DomainLogger {
    fun tag(tag: String): DomainLogger = this
    fun v(message: String, vararg args: Any?) = Unit
    fun d(message: String, vararg args: Any?) = Unit
    fun i(message: String, vararg args: Any?) = Unit
    fun w(message: String, vararg args: Any?) = Unit
    fun w(throwable: Throwable, message: String, vararg args: Any?) = Unit
    fun e(message: String, vararg args: Any?) = Unit
    fun e(throwable: Throwable, message: String, vararg args: Any?) = Unit
}
