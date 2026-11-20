package io.github.ratmux.sdhelper

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import io.github.ratmux.sdhelper.data.connection.ConnectionSupervisor
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SDHelperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SDHelperApp)
            modules(allModules)
        }
        val supervisor: ConnectionSupervisor = get()
        val localNetwork: LocalNetworkAccess = get()
        // Stay connected only while the app is visible; the permission may have changed while it was away.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    localNetwork.refresh()
                    supervisor.setForeground(true)
                } else if (event == Lifecycle.Event.ON_STOP) {
                    supervisor.setForeground(false)
                }
            },
        )
    }
}
