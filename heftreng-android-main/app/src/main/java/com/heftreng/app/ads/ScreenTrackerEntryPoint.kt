package com.heftreng.app.ads

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScreenTrackerEntryPoint {
    fun screenTracker(): ScreenTracker
}
