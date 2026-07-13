package org.openauto.companion.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.openauto.companion.CompanionApp
import org.openauto.companion.data.CompanionPrefs
import org.openauto.companion.net.CellularUpstreamMonitor
import org.openauto.companion.net.NetworkSocketFactory
import org.openauto.companion.net.Socks5Server
import org.openauto.companion.net.ThemeTransfer
import org.openauto.companion.net.api.ApiCrypto
import org.openauto.companion.net.api.ApiHandshake
import org.openauto.companion.net.api.ApiPairingCredentialStore
import org.openauto.companion.net.api.ApiReportPublisher
import org.openauto.companion.net.api.ApiRuntimeLoop
import org.openauto.companion.net.api.ApiSessionClient
import org.openauto.companion.net.api.ApiTcpTransport
import org.openauto.companion.ui.MainActivity

class CompanionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val themeExecutor = Executors.newSingleThreadExecutor()

    private var generationJob: Job? = null
    private var generationToken = 0L
    @Volatile
    private var currentResources: RuntimeResources? = null
    private var silentPlayer: SilentAudioPlayer? = null
    private var currentVehicleKey = ""
    private var currentVehicleName = "OpenAuto Prodigy"
    private var currentWifiNetwork: Network? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_AUDIO) {
            toggleSilentAudio()
            return START_STICKY
        }

        val config = parseRuntimeConfig(intent)
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
        if (config == null) {
            Log.e(TAG, "Refusing to start runtime without valid External API v1 credentials")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        _vehicleName.value = config.vehicleName
        _vehicleId.value = config.vehicleIdentity

        if (config.vehicleIdentity == currentVehicleKey &&
            config.wifiNetwork == currentWifiNetwork &&
            generationJob?.isActive == true
        ) {
            currentResources?.setInternetSharingEnabled(config.socks5Enabled)
            updateNotification(
                if (_connected.value) {
                    "Connected to $currentVehicleName"
                } else {
                    "Connecting to $currentVehicleName..."
                }
            )
            return START_STICKY
        }

        startRuntimeGeneration(config)
        return START_STICKY
    }

    private fun parseRuntimeConfig(intent: Intent?): RuntimeConfig? {
        val vehicleId = intent?.getStringExtra(EXTRA_VEHICLE_ID)?.trim().orEmpty()
        val vehicleSsid = intent?.getStringExtra(EXTRA_VEHICLE_SSID)?.trim().orEmpty()
        val vehicleIdentity = VehicleIdentity.resolve(vehicleId, vehicleSsid)
        val clientId = intent?.getStringExtra(EXTRA_API_CLIENT_ID)?.trim().orEmpty()
        val secret = ApiCrypto.decodeSecretHex(
            intent?.getStringExtra(EXTRA_API_SECRET_HEX)?.trim().orEmpty()
        )
        if (vehicleIdentity.isBlank() || clientId.isBlank() || secret == null) return null

        val host = intent?.getStringExtra(EXTRA_HEAD_UNIT_HOST)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: ApiTcpTransport.DEFAULT_HOST
        val wifiNetwork = (application as CompanionApp).wifiMonitor?.getWifiNetwork()
            ?: return null
        return RuntimeConfig(
            vehicleIdentity = vehicleIdentity,
            vehicleName = intent?.getStringExtra(EXTRA_VEHICLE_NAME)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "OpenAuto Prodigy",
            apiClientId = clientId,
            apiSecret = secret,
            serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
                ?.trim()
                ?.takeIf { it.isNotBlank() },
            host = host,
            wifiNetwork = wifiNetwork,
            socks5Enabled = intent?.getBooleanExtra(EXTRA_SOCKS5_ENABLED, true) ?: true,
            audioKeepAlive = intent?.getBooleanExtra(EXTRA_AUDIO_KEEP_ALIVE, false) ?: false
        )
    }

    private fun startRuntimeGeneration(config: RuntimeConfig) {
        val previousJob = generationJob
        val token = generationToken + 1
        generationToken = token
        currentVehicleKey = config.vehicleIdentity
        currentVehicleName = config.vehicleName
        currentWifiNetwork = config.wifiNetwork

        currentResources?.close()
        currentResources = null
        previousJob?.cancel()
        resetRuntimeState(clearVehicle = false)

        generationJob = serviceScope.launch {
            previousJob?.cancelAndJoin()
            if (!isActive || token != generationToken) return@launch
            runRuntimeGeneration(token, config)
        }
    }

    private suspend fun runRuntimeGeneration(token: Long, config: RuntimeConfig) = coroutineScope {
        val publisher = ApiReportPublisher(
            currentTimeMs = System::currentTimeMillis,
            timezoneId = { TimeZone.getDefault().id.takeIf(String::isNotBlank) }
        )
        val resources = RuntimeResources(
            token = token,
            publisher = publisher,
            proxyPassword = ProxyPassword.generate()
        )
        currentResources = resources

        try {
            resources.start(config.socks5Enabled)
            startSilentAudio(config.audioKeepAlive)
            val credentialStore = ApiPairingCredentialStore(
                loadVehicles = { CompanionPrefs(this@CompanionService).vehicles },
                saveVehicles = { CompanionPrefs(this@CompanionService).vehicles = it }
            )
            val clientScope = this
            val runtime = ApiRuntimeLoop(
                clientFactory = {
                    ApiSessionClient(
                        transport = ApiTcpTransport(
                            host = config.host,
                            port = ApiTcpTransport.DEFAULT_PORT,
                            socketFactory = NetworkSocketFactory.forNetwork(
                                config.wifiNetwork,
                                onFallback = {
                                    Log.w(
                                        TAG,
                                        "Wi-Fi-bound API v1 socket was denied; retrying this socket unbound"
                                    )
                                }
                            )
                        ),
                        handshake = ApiHandshake.knownClient(
                            clientName = CLIENT_NAME,
                            clientId = config.apiClientId,
                            secret = config.apiSecret
                        ),
                        scope = clientScope
                    )
                },
                reportPublisher = publisher,
                storedServerId = config.serverId,
                retryDelay = ::delayForRetry,
                onStateChanged = { state -> handleRuntimeState(token, state, resources) },
                persistSystemStatus = { status ->
                    if (token != generationToken) return@ApiRuntimeLoop
                    if (credentialStore.persistSystemStatus(config.vehicleIdentity, status)) {
                        _displayWidth.value = status.displayWidth
                        _displayHeight.value = status.displayHeight
                    }
                }
            )

            when (val exit = runtime.run()) {
                is ApiRuntimeLoop.Exit.IdentityMismatch -> {
                    Log.w(TAG, "External API server identity mismatch; re-pair required")
                }
                is ApiRuntimeLoop.Exit.RePairRequired -> {
                    Log.w(TAG, "External API credentials were rejected; re-pair required")
                }
            }
        } finally {
            resources.close()
            if (currentResources === resources) currentResources = null
            if (token == generationToken) {
                _connected.value = false
                _socks5Active.value = false
                stopSilentAudio()
            }
        }
    }

    private suspend fun delayForRetry(attempt: Int) {
        val base = RETRY_DELAYS_MS[attempt.coerceIn(0, RETRY_DELAYS_MS.lastIndex)]
        val jitter = Random.nextLong(0L, (base / 4L) + 1L)
        delay(base + jitter)
    }

    private fun handleRuntimeState(
        token: Long,
        state: ApiRuntimeLoop.State,
        resources: RuntimeResources
    ) {
        if (token != generationToken) return
        when (state) {
            is ApiRuntimeLoop.State.Connecting -> {
                _connected.value = false
                _connectionIssue.value = null
                resources.refreshConnectivity()
                updateNotification("Connecting to $currentVehicleName...")
            }
            is ApiRuntimeLoop.State.Ready -> {
                _connected.value = true
                _connectionIssue.value = null
                resources.refreshConnectivity()
                updateNotification("Connected to $currentVehicleName")
            }
            is ApiRuntimeLoop.State.WaitingToRetry -> {
                _connected.value = false
                resources.refreshConnectivity()
                updateNotification("Connection lost — retrying...")
            }
            is ApiRuntimeLoop.State.RePairRequired -> {
                _connected.value = false
                _connectionIssue.value = "Authentication failed — re-pair required"
                resources.refreshConnectivity()
                updateNotification("Re-pair required for $currentVehicleName")
            }
            is ApiRuntimeLoop.State.IdentityMismatch -> {
                _connected.value = false
                _connectionIssue.value = "Vehicle identity changed — re-pair required"
                resources.refreshConnectivity()
                updateNotification("Re-pair required for $currentVehicleName")
            }
        }
    }

    private fun setInternetSharingEnabled(vehicleId: String, enabled: Boolean): Boolean {
        val normalized = vehicleId.trim()
        if (normalized.isBlank() || normalized != currentVehicleKey) return false
        val resources = currentResources ?: return false
        serviceScope.launch {
            if (normalized == currentVehicleKey && resources === currentResources) {
                resources.setInternetSharingEnabled(enabled)
            }
        }
        return true
    }

    private inner class RuntimeResources(
        private val token: Long,
        private val publisher: ApiReportPublisher,
        private val proxyPassword: String
    ) : AutoCloseable {
        private val locationMapper = LocationReportMapper()
        private val locationManager = getSystemService(LocationManager::class.java)
        private val cellularMonitor = CellularUpstreamMonitor(
            getSystemService(ConnectivityManager::class.java)
        ) { refreshConnectivity() }
        private var receiverRegistered = false
        private var locationRegistered = false
        @Volatile
        private var socksServer: Socks5Server? = null
        @Volatile
        private var closed = false

        private val reportReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (closed || token != generationToken) return
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> publisher.notifyTimeChanged()
                }
            }
        }

        private val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (closed || token != generationToken) return
                val sample = LocationSample(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speedMps = location.speed.toDouble().takeIf { location.hasSpeed() },
                    bearingDeg = location.bearing.toDouble().takeIf { location.hasBearing() },
                    accuracyM = location.accuracy.toDouble().takeIf { location.hasAccuracy() },
                    altitudeM = location.altitude.takeIf { location.hasAltitude() },
                    elapsedRealtimeNanos = location.elapsedRealtimeNanos
                )
                locationMapper.map(sample, SystemClock.elapsedRealtimeNanos())
                    ?.let(publisher::updateGps)
            }
        }

        fun start(internetSharingEnabled: Boolean) {
            readInitialBattery()
            registerReportReceiver()
            startLocationUpdates()
            try {
                cellularMonitor.start()
            } catch (error: Exception) {
                Log.w(TAG, "Unable to monitor cellular upstream", error)
            }
            setInternetSharingEnabled(internetSharingEnabled)
        }

        fun setInternetSharingEnabled(enabled: Boolean) {
            if (closed) return
            if (enabled) startSocksServer() else stopSocksServer()
            refreshConnectivity()
        }

        fun refreshConnectivity() {
            val internetAvailable = !closed && cellularMonitor.internetAvailable
            val server = socksServer
            val listenerActive = !closed && server?.isActive == true
            val port = if (listenerActive) server?.listeningPort ?: 0 else 0
            publisher.updateConnectivity(
                ApiReportPublisher.ConnectivitySnapshot(
                    internetAvailable = internetAvailable,
                    socks5Active = listenerActive,
                    socks5Port = port,
                    socks5Password = proxyPassword.takeIf { listenerActive }
                )
            )
            if (token == generationToken) {
                _socks5Active.value = _connected.value && internetAvailable && listenerActive
            }
        }

        private fun startSocksServer() {
            if (socksServer?.isActive == true) return
            val candidate = Socks5Server(
                port = SOCKS5_PORT,
                password = proxyPassword,
                upstreamNetworkProvider = { cellularMonitor.currentNetwork }
            )
            try {
                candidate.start()
                socksServer = candidate
                Log.i(TAG, "SOCKS5 proxy listener started")
            } catch (error: Exception) {
                candidate.stop()
                Log.e(TAG, "Failed to start SOCKS5 proxy listener", error)
            }
        }

        private fun stopSocksServer() {
            val previous = socksServer
            socksServer = null
            previous?.stop()
        }

        private fun readInitialBattery() {
            val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (sticky != null) updateBattery(sticky)
        }

        private fun registerReportReceiver() {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(
                this@CompanionService,
                reportReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }

        private fun updateBattery(intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val percent = ((level.toLong() * 100L) / scale.toLong()).toInt().coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            publisher.updateBattery(ApiReportPublisher.BatterySnapshot(percent, charging))
        }

        private fun startLocationUpdates() {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.FUSED_PROVIDER,
                    LOCATION_INTERVAL_MS,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
                locationRegistered = true
            } catch (error: Exception) {
                if (error is SecurityException || error is IllegalArgumentException) {
                    try {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            LOCATION_INTERVAL_MS,
                            0f,
                            locationListener,
                            Looper.getMainLooper()
                        )
                        locationRegistered = true
                    } catch (fallbackError: Exception) {
                        Log.w(TAG, "Location updates unavailable", fallbackError)
                    }
                } else {
                    throw error
                }
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            stopSocksServer()
            cellularMonitor.close()
            if (locationRegistered) {
                locationManager.removeUpdates(locationListener)
                locationRegistered = false
            }
            if (receiverRegistered) {
                try {
                    unregisterReceiver(reportReceiver)
                } catch (_: IllegalArgumentException) {
                }
                receiverRegistered = false
            }
            publisher.updateGps(null)
            refreshConnectivity()
        }
    }

    private fun startSilentAudio(enabled: Boolean) {
        if (!enabled || silentPlayer?.isActive == true) return
        silentPlayer = SilentAudioPlayer().also { it.start() }
        _audioKeepAliveActive.value = true
        Log.i(TAG, "Audio keep alive started")
    }

    private fun stopSilentAudio() {
        silentPlayer?.stop()
        silentPlayer = null
        _audioKeepAliveActive.value = false
    }

    private fun toggleSilentAudio() {
        if (silentPlayer?.isActive == true) {
            stopSilentAudio()
            Log.i(TAG, "Audio keep alive toggled OFF")
        } else {
            startSilentAudio(enabled = true)
            Log.i(TAG, "Audio keep alive toggled ON")
        }
        if (_connected.value) updateNotification("Connected to $currentVehicleName")
    }

    private fun resetRuntimeState(clearVehicle: Boolean) {
        _connected.value = false
        _socks5Active.value = false
        _audioKeepAliveActive.value = false
        _displayWidth.value = null
        _displayHeight.value = null
        _connectionIssue.value = null
        _themeTransferResult.value = null
        stopSilentAudio()
        if (clearVehicle) {
            _vehicleName.value = ""
            _vehicleId.value = ""
            currentVehicleKey = ""
            currentWifiNetwork = null
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CompanionApp.CHANNEL_ID)
            .setContentTitle("OpenAuto Companion")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (_connected.value) {
            val toggleIntent = Intent(this, CompanionService::class.java).apply {
                action = ACTION_TOGGLE_AUDIO
            }
            val togglePending = PendingIntent.getService(
                this,
                1,
                toggleIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val label = if (_audioKeepAliveActive.value) {
                "\uD83D\uDD0A Audio Alive: ON"
            } else {
                "\uD83D\uDD07 Audio Alive: OFF"
            }
            builder.addAction(0, label, togglePending)
        }
        return builder.build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    fun sendTheme(settingsHost: String?, themeJson: JSONObject, wallpaperBytes: ByteArray) {
        val targetHost = settingsHost?.trim()?.ifBlank { null }
        val logTargetHost = targetHost ?: ApiTcpTransport.DEFAULT_HOST
        Log.i(
            TAG,
            "sendTheme: dispatching transfer to $logTargetHost (wallpaper=${wallpaperBytes.size} bytes)"
        )
        _themeTransferResult.value = null
        themeExecutor.execute {
            val wifiNetwork = (application as CompanionApp).wifiMonitor?.getWifiNetwork()
            val boundClient = wifiNetwork?.let {
                OkHttpClient.Builder().socketFactory(it.socketFactory).build()
            }
            try {
                _themeTransferResult.value = if (boundClient != null) {
                    ThemeTransfer.send(targetHost, themeJson, wallpaperBytes, boundClient)
                } else {
                    ThemeTransfer.send(targetHost, themeJson, wallpaperBytes)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Theme transfer failed", error)
                _themeTransferResult.value = ThemeTransfer.TransferResult.Failed(
                    error.message ?: "Transfer failed"
                )
            } finally {
                boundClient?.shutdownForRequest()
            }
        }
    }

    private fun OkHttpClient.shutdownForRequest() {
        dispatcher.executorService.shutdown()
        connectionPool.evictAll()
    }

    override fun onDestroy() {
        instance = null
        generationToken += 1
        generationJob?.cancel()
        generationJob = null
        currentResources?.close()
        currentResources = null
        resetRuntimeState(clearVehicle = true)
        serviceScope.cancel()
        themeExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class RuntimeConfig(
        val vehicleIdentity: String,
        val vehicleName: String,
        val apiClientId: String,
        val apiSecret: ByteArray,
        val serverId: String?,
        val host: String,
        val wifiNetwork: Network,
        val socks5Enabled: Boolean,
        val audioKeepAlive: Boolean
    )

    companion object {
        private const val TAG = "CompanionService"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_TOGGLE_AUDIO = "org.openauto.companion.TOGGLE_AUDIO"
        private const val CLIENT_NAME = "OpenAuto Companion"
        private const val SOCKS5_PORT = 1080
        private const val LOCATION_INTERVAL_MS = 1_000L
        private val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

        const val EXTRA_VEHICLE_ID = "vehicle_id"
        const val EXTRA_VEHICLE_SSID = "vehicle_ssid"
        const val EXTRA_VEHICLE_NAME = "vehicle_name"
        const val EXTRA_API_CLIENT_ID = "api_client_id"
        const val EXTRA_API_SECRET_HEX = "api_secret_hex"
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_HEAD_UNIT_HOST = "head_unit_host"
        const val EXTRA_SOCKS5_ENABLED = "socks5_enabled"
        const val EXTRA_AUDIO_KEEP_ALIVE = "audio_keep_alive"

        private var instance: CompanionService? = null

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val _socks5Active = MutableStateFlow(false)
        val socks5Active: StateFlow<Boolean> = _socks5Active.asStateFlow()

        private val _audioKeepAliveActive = MutableStateFlow(false)
        val audioKeepAliveActive: StateFlow<Boolean> = _audioKeepAliveActive.asStateFlow()

        private val _vehicleName = MutableStateFlow("")
        val vehicleName: StateFlow<String> = _vehicleName.asStateFlow()

        private val _vehicleId = MutableStateFlow("")
        val vehicleId: StateFlow<String> = _vehicleId.asStateFlow()

        private val _displayWidth = MutableStateFlow<Int?>(null)
        val displayWidth: StateFlow<Int?> = _displayWidth.asStateFlow()

        private val _displayHeight = MutableStateFlow<Int?>(null)
        val displayHeight: StateFlow<Int?> = _displayHeight.asStateFlow()

        private val _connectionIssue = MutableStateFlow<String?>(null)
        val connectionIssue: StateFlow<String?> = _connectionIssue.asStateFlow()

        private val _themeTransferResult = MutableStateFlow<ThemeTransfer.TransferResult?>(null)
        val themeTransferResult: StateFlow<ThemeTransfer.TransferResult?> =
            _themeTransferResult.asStateFlow()

        fun setInternetSharingStatic(vehicleId: String, enabled: Boolean): Boolean =
            instance?.setInternetSharingEnabled(vehicleId, enabled) ?: false

        fun sendThemeStatic(
            settingsHost: String?,
            themeJson: JSONObject,
            wallpaperBytes: ByteArray
        ) {
            instance?.sendTheme(settingsHost, themeJson, wallpaperBytes)
                ?: run {
                    _themeTransferResult.value =
                        ThemeTransfer.TransferResult.Failed("Service not running")
                }
        }

        fun clearThemeTransferResult() {
            _themeTransferResult.value = null
        }
    }
}
