package com.mindflow.app

import android.app.Application
import com.mindflow.app.di.AppContainer
import com.mindflow.app.util.CrashLogReporter

class MindFlowApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogReporter(this).install()
        appContainer = AppContainer(this)
    }
}
