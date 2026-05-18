package com.example.liftrix.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeWidgetEntryPoint {
    fun dataSource(): LiftrixHomeWidgetDataSource
    fun updateScheduler(): LiftrixWidgetUpdateScheduler
}
