package com.example.liftrix.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.example.liftrix.domain.model.RestTimer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service binding wrapper for ViewModel integration with WorkoutTimerService.
 * Provides a reactive interface for timer state management and handles service lifecycle.
 */
@Singleton
class TimerServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var timerService: WorkoutTimerService? = null
    private var isBound = false
    private var isBindingInProgress = false
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _timerState = MutableStateFlow(WorkoutTimerService.TimerServiceState())
    val timerState: StateFlow<WorkoutTimerService.TimerServiceState> = _timerState.asStateFlow()
    
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val exception: Throwable) : ConnectionState()
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as WorkoutTimerService.TimerBinder
                timerService = binder.getService()
                isBound = true
                isBindingInProgress = false
                _connectionState.value = ConnectionState.Connected
                
                // Subscribe to service state updates
                managerScope.launch {
                    timerService?.serviceState?.collect { state ->
                        _timerState.value = state
                    }
                }
                
                Timber.d("TimerServiceManager connected to WorkoutTimerService")
            } catch (e: Exception) {
                handleConnectionError(e)
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
            isBindingInProgress = false
            _connectionState.value = ConnectionState.Disconnected
            _timerState.value = WorkoutTimerService.TimerServiceState()
            Timber.d("TimerServiceManager disconnected from WorkoutTimerService")
        }
        
        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            handleConnectionError(IllegalStateException("Service binding died"))
        }
        
        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            handleConnectionError(IllegalStateException("Service returned null binding"))
        }
    }
    
    /**
     * Binds to the WorkoutTimerService. Should be called when timer functionality is needed.
     */
    fun bindService(): Result<Unit> {
        return try {
            if (isBound || isBindingInProgress) {
                Timber.d("Service already bound or binding in progress")
                return Result.success(Unit)
            }
            
            isBindingInProgress = true
            _connectionState.value = ConnectionState.Connecting
            
            val intent = Intent(context, WorkoutTimerService::class.java)
            val bindResult = context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            
            if (!bindResult) {
                isBindingInProgress = false
                val error = IllegalStateException("Failed to bind to WorkoutTimerService")
                handleConnectionError(error)
                Result.failure(error)
            } else {
                Timber.d("TimerServiceManager binding to WorkoutTimerService")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            isBindingInProgress = false
            handleConnectionError(e)
            Result.failure(e)
        }
    }
    
    /**
     * Unbinds from the WorkoutTimerService. Should be called when timer functionality is no longer needed.
     */
    fun unbindService(): Result<Unit> {
        return try {
            if (isBound) {
                context.unbindService(serviceConnection)
                timerService = null
                isBound = false
                isBindingInProgress = false
                _connectionState.value = ConnectionState.Disconnected
                _timerState.value = WorkoutTimerService.TimerServiceState()
                Timber.d("TimerServiceManager unbound from WorkoutTimerService")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unbind from WorkoutTimerService")
            Result.failure(e)
        }
    }
    
    /**
     * Starts a workout session timer
     */
    fun startSession(): Result<Unit> {
        return executeServiceOperation("startSession") { service ->
            service.startSession()
        }
    }
    
    /**
     * Pauses the current timer (session or rest)
     */
    fun pauseTimer(): Result<Unit> {
        return executeServiceOperation("pauseTimer") { service ->
            service.pauseTimer()
        }
    }
    
    /**
     * Resumes the paused timer
     */
    fun resumeTimer(): Result<Unit> {
        return executeServiceOperation("resumeTimer") { service ->
            service.resumeTimer()
        }
    }
    
    /**
     * Starts a rest timer with the specified configuration
     */
    fun startRestTimer(restTimer: RestTimer): Result<Unit> {
        return executeServiceOperation("startRestTimer") { service ->
            service.startRestTimer(restTimer)
        }
    }
    
    /**
     * Skips the current rest timer
     */
    fun skipRest(): Result<Unit> {
        return executeServiceOperation("skipRest") { service ->
            service.skipRest()
        }
    }

    /**
     * Adjusts the active or paused rest timer by the provided number of seconds.
     */
    fun adjustRestBySeconds(deltaSeconds: Int): Result<Unit> {
        return executeServiceOperation("adjustRestBySeconds") { service ->
            service.adjustRestBySeconds(deltaSeconds)
        }
    }
    
    /**
     * Stops all timers and ends the service
     */
    fun stopTimer(): Result<Unit> {
        return executeServiceOperation("stopTimer") { service ->
            service.stopTimer()
        }
    }
    
    /**
     * Checks if the service is currently bound and available
     */
    fun isServiceBound(): Boolean = isBound && timerService != null
    
    /**
     * Gets the current timer state (synchronous access to latest state)
     */
    fun getCurrentTimerState(): WorkoutTimerService.TimerServiceState = _timerState.value
    
    private inline fun executeServiceOperation(
        operationName: String,
        operation: (WorkoutTimerService) -> Result<Unit>
    ): Result<Unit> {
        return try {
            val service = timerService
            if (!isBound || service == null) {
                val error = IllegalStateException("Service not bound. Call bindService() first.")
                Timber.w("Cannot execute $operationName: service not bound")
                Result.failure(error)
            } else {
                val result = operation(service)
                if (result.isFailure) {
                    Timber.w("Service operation $operationName failed: ${result.exceptionOrNull()}")
                } else {
                    Timber.d("Service operation $operationName completed successfully")
                }
                result
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during service operation $operationName")
            Result.failure(e)
        }
    }
    
    private fun handleConnectionError(exception: Throwable) {
        timerService = null
        isBound = false
        isBindingInProgress = false
        _connectionState.value = ConnectionState.Error(exception)
        _timerState.value = WorkoutTimerService.TimerServiceState()
        Timber.e(exception, "TimerServiceManager connection error")
    }
}
