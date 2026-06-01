package com.example.liftrix.startup

import com.example.liftrix.monitoring.PerformanceMonitor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupTaskTracer @Inject constructor(
    private val performanceMonitor: PerformanceMonitor
) {
    fun <T> measure(
        name: String,
        taskClass: StartupTaskClass,
        block: () -> T
    ): T {
        val startedAt = System.currentTimeMillis()
        val trace = performanceMonitor.startCustomTrace("startup_${name}_${taskClass.name}")
        Timber.i("StartupTask name=$name class=${taskClass.name} status=started")
        return try {
            block().also {
                val durationMs = System.currentTimeMillis() - startedAt
                trace?.let {
                    performanceMonitor.stopCustomTrace(
                        it,
                        attributes = mapOf("task_class" to taskClass.name, "task_name" to name),
                        metrics = mapOf("duration_ms" to durationMs)
                    )
                }
                Timber.i("StartupTask name=$name class=${taskClass.name} status=finished durationMs=$durationMs")
            }
        } catch (error: Throwable) {
            val durationMs = System.currentTimeMillis() - startedAt
            trace?.let {
                performanceMonitor.stopCustomTrace(
                    it,
                    attributes = mapOf(
                        "task_class" to taskClass.name,
                        "task_name" to name,
                        "status" to "failed"
                    ),
                    metrics = mapOf("duration_ms" to durationMs)
                )
            }
            Timber.e(error, "StartupTask name=$name class=${taskClass.name} status=failed durationMs=$durationMs")
            throw error
        }
    }

    suspend fun <T> measureSuspend(
        name: String,
        taskClass: StartupTaskClass,
        block: suspend () -> T
    ): T {
        val startedAt = System.currentTimeMillis()
        val trace = performanceMonitor.startCustomTrace("startup_${name}_${taskClass.name}")
        Timber.i("StartupTask name=$name class=${taskClass.name} status=started")
        return try {
            block().also {
                val durationMs = System.currentTimeMillis() - startedAt
                trace?.let {
                    performanceMonitor.stopCustomTrace(
                        it,
                        attributes = mapOf("task_class" to taskClass.name, "task_name" to name),
                        metrics = mapOf("duration_ms" to durationMs)
                    )
                }
                Timber.i("StartupTask name=$name class=${taskClass.name} status=finished durationMs=$durationMs")
            }
        } catch (error: Throwable) {
            val durationMs = System.currentTimeMillis() - startedAt
            trace?.let {
                performanceMonitor.stopCustomTrace(
                    it,
                    attributes = mapOf(
                        "task_class" to taskClass.name,
                        "task_name" to name,
                        "status" to "failed"
                    ),
                    metrics = mapOf("duration_ms" to durationMs)
                )
            }
            Timber.e(error, "StartupTask name=$name class=${taskClass.name} status=failed durationMs=$durationMs")
            throw error
        }
    }
}
