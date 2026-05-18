package com.example.liftrix.widget

import android.content.Context
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import com.example.liftrix.MainActivity

object LiftrixWidgetActions {
    const val ACTION_OPEN_WORKOUT = "com.example.liftrix.widget.OPEN_WORKOUT"
    const val EXTRA_WIDGET_SOURCE = "com.example.liftrix.widget.EXTRA_WIDGET_SOURCE"

    fun openWorkoutIntent(context: Context, source: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_WORKOUT
            putExtra(EXTRA_WIDGET_SOURCE, source)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun openWorkoutAction(context: Context, source: String): Action {
        return actionStartActivity<MainActivity>(
            actionParametersOf(
                ActionParameters.Key<String>(EXTRA_WIDGET_SOURCE).to(source)
            )
        )
    }
}
