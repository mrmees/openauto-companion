package org.openauto.companion

internal interface MonitorLifecycle {
    fun start()
    fun stop(stopService: Boolean)
}

internal class MonitorSlot<T : MonitorLifecycle> {
    var current: T? = null
        private set

    fun replace(next: T) {
        current?.stop(stopService = false)
        current = next
        next.start()
    }

    fun stopCurrent() {
        current?.stop(stopService = true)
        current = null
    }
}
