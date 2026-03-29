package com.mindflow.app

import android.app.Application
import com.mindflow.app.di.AppContainer

class MindFlowApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
