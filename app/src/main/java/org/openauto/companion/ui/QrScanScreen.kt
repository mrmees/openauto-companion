package org.openauto.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import android.util.Size
import org.openauto.companion.net.PairingUriParser
import java.util.concurrent.Executors

private const val TAG = "QrScanScreen"

@Composable
fun QrScanScreen(
    onScanned: (ssid: String, pin: String, vehicleId: String?, host: String?, port: Int?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) permissionDenied = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            hasCameraPermission -> {
                Text(
                    "Scan QR Code",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    "Point your camera at the QR code on the head unit",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                CameraPreviewWithScanner(
                    onScanned = onScanned,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                )

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Cancel")
                }
            }
            permissionDenied -> {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Camera permission is required to scan QR codes.",
                    modifier = Modifier.padding(32.dp)
                )
                TextButton(onClick = onCancel) {
                    Text("Go Back")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            else -> {
                // Waiting for permission dialog
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Requesting camera permission...")
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CameraPreviewWithScanner(
    onScanned: (ssid: String, pin: String, vehicleId: String?, host: String?, port: Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scanComplete by remember { mutableStateOf(false) }

    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(1280, 720))
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    if (scanComplete) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    processFrame(imageProxy, scanner) { ssid, pin, vehicleId, host, port ->
                        if (!scanComplete) {
                            scanComplete = true
                            onScanned(ssid, pin, vehicleId, host, port)
                        }
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

private var frameCount = 0L

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processFrame(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onResult: (ssid: String, pin: String, vehicleId: String?, host: String?, port: Int?) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    frameCount++
    if (frameCount % 30 == 0L) {
        Log.d(TAG, "Analyzed $frameCount frames, rotation=${imageProxy.imageInfo.rotationDegrees}, " +
                "size=${mediaImage.width}x${mediaImage.height}")
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            if (barcodes.isNotEmpty()) {
                Log.i(TAG, "ML Kit found ${barcodes.size} barcode(s)")
            }
            for (barcode in barcodes) {
                val rawValue = barcode.rawValue
                Log.i(TAG, "Barcode raw: $rawValue format=${barcode.format} type=${barcode.valueType}")
                if (rawValue == null) continue
                val payload = PairingUriParser.parse(rawValue)
                if (payload != null) {
                    Log.i(TAG, "QR scanned: ssid=${payload.ssid}")
                    onResult(payload.ssid, payload.pin, payload.vehicleId, payload.host, payload.port)
                    break
                }
                Log.w(TAG, "QR payload invalid: $rawValue")
            }
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Barcode scan failed", e)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
