package app.getkosha

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.getkosha.auth.AppLock
import app.getkosha.data.Kosha

class KoshaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val kosha = Kosha.init(this)
        AppLock.launched()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.foregrounded()
                kosha.refresh()
            }

            override fun onStop(owner: LifecycleOwner) = AppLock.backgrounded()
        })
    }
}
