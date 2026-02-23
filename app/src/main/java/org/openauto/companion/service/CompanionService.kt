package org.openauto.companion.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CompanionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
