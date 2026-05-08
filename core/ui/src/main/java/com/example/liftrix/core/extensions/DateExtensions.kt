@file:kotlin.jvm.JvmName("UiDateExtensionsKt")

package com.example.liftrix.core.extensions

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

fun Date.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    toInstant().atZone(zoneId).toLocalDate()

fun Long.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
