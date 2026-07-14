package org.openauto.companion

internal interface MonitorLifecycle {
    fun start()
    fun stop()
}

internal class MonitorSlot<T : MonitorLifecycle> {
    var current: T? = null
        private set

    fun startIfAbsent(next: T) {
        if (current != null) return
        current = next
        next.start()
    }

    fun stopCurrent() {
        current?.stop()
        current = null
    }
}
