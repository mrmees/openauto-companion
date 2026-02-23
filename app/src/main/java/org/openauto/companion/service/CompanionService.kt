package org.openauto.companion.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import org.openauto.companion.CompanionApp
import org.openauto.companion.net.PiConnection
import org.openauto.companion.net.Protocol
import org.openauto.companion.net.Socks5Server
import org.openauto.companion.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CompanionService : Service() {
    private var connection: PiConnection? = null
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var pushTask: ScheduledFuture<*>? = null
    private val seq = AtomicInteger(0)
    private var socks5Server: Socks5Server? = null

    private var lastLocation: Location? = null
    private var locationManager: LocationManager? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val secret = intent?.getStringExtra("shared_secret") ?: ""
        val vehicleName = intent?.getStringExtra("vehicle_name") ?: "OpenAuto Prodigy"
        val socks5EnabledOverride = intent?.getBooleanExtra("socks5_enabled", true) ?: true
        _vehicleName.value = vehicleName
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))

        startLocationUpdates()

        executor.execute {
            val wifiNetwork = (application as org.openauto.companion.CompanionApp).wifiMonitor?.getWifiNetwork()
            Log.i(TAG, "WiFi network for binding: $wifiNetwork")
            val conn = PiConnection(sharedSecret = secret, wifiNetwork = wifiNetwork)
            if (conn.connect()) {
                connection = conn
                _connected.value = true
                Log.i(TAG, "Connection established, _connected = true")
                startSocks5(secret, socks5EnabledOverride)
                updateNotification("Connected to $vehicleName")
                startPushLoop()
            } else {
                updateNotification("Connection failed — retrying...")
                // Retry after 10s
                executor.schedule({
                    onStartCommand(intent, flags, startId)
                }, 10, TimeUnit.SECONDS)
            }
        }

        return START_STICKY
    }

    private fun startPushLoop() {
        pushTask = executor.scheduleAtFixedRate({
            try {
                val conn = connection ?: return@scheduleAtFixedRate
                val key = conn.sessionKey ?: return@scheduleAtFixedRate
                if (!conn.isConnected()) {
                    _connected.value = false
                    stopSelf()
                    return@scheduleAtFixedRate
                }

                val loc = lastLocation
                val gpsAgeMs = if (loc != null) {
                    ((SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000).toInt()
                } else -1

                val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val batteryLevel = batteryIntent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    (level * 100) / scale
                } ?: -1
                val charging = batteryIntent?.let {
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                } ?: false

                val status = Protocol.buildStatus(
                    seq = seq.incrementAndGet(),
                    sessionKey = key,
                    timeMs = System.currentTimeMillis(),
                    timezone = TimeZone.getDefault().id,
                    gpsLat = loc?.latitude ?: 0.0,
                    gpsLon = loc?.longitude ?: 0.0,
                    gpsAccuracy = loc?.accuracy?.toDouble() ?: 0.0,
                    gpsSpeed = loc?.speed?.toDouble() ?: 0.0,
                    gpsBearing = loc?.bearing?.toDouble() ?: 0.0,
                    gpsAgeMs = gpsAgeMs,
                    batteryLevel = batteryLevel,
                    batteryCharging = charging,
                    socks5Port = 1080,
                    socks5Active = socks5Server?.isActive ?: false
                )

                conn.sendStatus(status)
            } catch (e: Exception) {
                Log.e(TAG, "Push failed", e)
            }
        }, 0, 5, TimeUnit.SECONDS)
    }

    private fun startSocks5(secret: String, enabled: Boolean = true) {
        if (!enabled) {
            Log.i(TAG, "SOCKS5 proxy disabled for this vehicle")
            return
        }
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            // Use "oap" + first 8 chars of secret as credentials
            val user = "oap"
            val pass = if (secret.length >= 8) secret.substring(0, 8) else secret
            socks5Server = Socks5Server(
                port = 1080,
                username = user,
                password = pass,
                connectivityManager = cm
            )
            socks5Server!!.start()
            _socks5Active.value = true
            Log.i(TAG, "SOCKS5 proxy started on port 1080 (user=$user)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SOCKS5 proxy", e)
            socks5Server = null
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.FUSED_PROVIDER,
                5000L, 0f, locationListener, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted", e)
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000L, 0f, locationListener, Looper.getMainLooper()
                )
            } catch (_: SecurityException) {
                Log.e(TAG, "No location permission at all")
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CompanionApp.CHANNEL_ID)
            .setContentTitle("OpenAuto Companion")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        _connected.value = false
        _socks5Active.value = false
        _vehicleName.value = ""
        pushTask?.cancel(false)
        socks5Server?.stop()
        socks5Server = null
        executor.execute {
            connection?.disconnect()
        }
        locationManager?.removeUpdates(locationListener)
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CompanionService"
        private const val NOTIFICATION_ID = 1001

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val _socks5Active = MutableStateFlow(false)
        val socks5Active: StateFlow<Boolean> = _socks5Active.asStateFlow()

        private val _vehicleName = MutableStateFlow("")
        val vehicleName: StateFlow<String> = _vehicleName.asStateFlow()
    }
}
