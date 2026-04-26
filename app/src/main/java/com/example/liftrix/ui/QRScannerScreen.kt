package com.example.liftrix.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.QRCodeService
import com.example.liftrix.ui.theme.LiftrixTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * QR code scanner screen with camera integration using CameraX and ZXing
 * Features gym buddy pairing with QR code detection and processing
 */
@Composable
fun QRScannerScreen(
    onQrCodeScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QRScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var hasCameraPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Check initial camera permission
    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            viewModel.handleEvent(QRScannerEvent.PermissionGranted)
        } else {
            viewModel.handleEvent(QRScannerEvent.PermissionDenied)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.handleEvent(QRScannerEvent.InitializeScanner)
    }

    LaunchedEffect(uiState.connectionSuccess, uiState.connectedBuddyId) {
        if (uiState.connectionSuccess) {
            uiState.connectedBuddyId?.let { buddyId ->
                onQrCodeScanned(buddyId)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewWithOverlay(
                onQRCodeDetected = { qrCode ->
                    viewModel.handleEvent(QRScannerEvent.CodeScanned(qrCode))
                },
                isProcessing = uiState.isProcessing,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CameraPermissionRequest(
                onRequestPermission = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top bar
        QRScannerTopBar(
            onNavigateBack = onNavigateBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Scanning overlay and status
        QRScanningOverlay(
            isProcessing = uiState.isProcessing,
            error = uiState.error,
            successMessage = uiState.successMessage,
            onRetry = { viewModel.handleEvent(QRScannerEvent.RetryScanning) },
            modifier = Modifier.align(Alignment.Center)
        )

        // Bottom instruction panel
        QRScannerInstructions(
            isProcessing = uiState.isProcessing,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Camera preview with QR code detection overlay
 */
@Composable
private fun CameraPreviewWithOverlay(
    onQRCodeDetected: (String) -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val qrCodeReader = remember { MultiFormatReader() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx: Context ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            
            cameraProviderFuture.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                
                // Preview use case
                val preview = CameraPreview.Builder()
                    .setTargetResolution(Size(1080, 1920))
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Image analysis for QR code detection
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1080, 1920))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (!isProcessing) {
                        analyzeQRCode(imageProxy, qrCodeReader, onQRCodeDetected)
                    }
                    imageProxy.close()
                }

                // Camera selector
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Timber.e(exc, "Camera binding failed")
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        },
        modifier = modifier
    )
}

/**
 * Analyzes camera frames for QR codes using ZXing
 */
private fun analyzeQRCode(
    imageProxy: ImageProxy,
    reader: MultiFormatReader,
    onQRCodeDetected: (String) -> Unit
) {
    try {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val yBuffer = mediaImage.planes[0].buffer
            val yData = ByteArray(yBuffer.remaining())
            yBuffer.get(yData)

            val source = PlanarYUVLuminanceSource(
                yData,
                mediaImage.width,
                mediaImage.height,
                0,
                0,
                mediaImage.width,
                mediaImage.height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )

            try {
                val result = reader.decode(binaryBitmap, hints)
                onQRCodeDetected(result.text)
            } catch (e: Exception) {
                // No QR code found in this frame, continue scanning
            } finally {
                reader.reset()
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Error analyzing image for QR code")
    }
}

/**
 * Converts ImageProxy to Bitmap for QR code analysis
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val image = imageProxy.image ?: return null
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
        Timber.w(e, "Error converting ImageProxy to Bitmap")
        null
    }
}

/**
 * Top bar with back navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QRScannerTopBar(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = "Scan QR Code",
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.5f)
        ),
        modifier = modifier
    )
}

/**
 * QR scanning overlay with viewfinder
 */
@Composable
private fun QRScanningOverlay(
    isProcessing: Boolean,
    error: String?,
    successMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Viewfinder frame
        Canvas(
            modifier = Modifier.size(280.dp)
        ) {
            val strokeWidth = 4.dp.toPx()
            val cornerLength = 40.dp.toPx()
            
            // Draw corner lines for viewfinder
            // Top-left corner
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(0f, cornerLength),
                end = androidx.compose.ui.geometry.Offset(0f, 0f),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(cornerLength, 0f),
                strokeWidth = strokeWidth
            )
            
            // Top-right corner
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(size.width - cornerLength, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, cornerLength),
                strokeWidth = strokeWidth
            )
            
            // Bottom-left corner
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(0f, size.height - cornerLength),
                end = androidx.compose.ui.geometry.Offset(0f, size.height),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(0f, size.height),
                end = androidx.compose.ui.geometry.Offset(cornerLength, size.height),
                strokeWidth = strokeWidth
            )
            
            // Bottom-right corner
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(size.width - cornerLength, size.height),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(size.width, size.height),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height - cornerLength),
                strokeWidth = strokeWidth
            )
        }

        // Processing indicator
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = "Processing QR Code...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Error state
        if (error != null) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(
                        Color.Red.copy(alpha = 0.8f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = error,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Red
                        )
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        if (successMessage != null) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(
                        Color(0xFF16A34A).copy(alpha = 0.9f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = successMessage,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Bottom instructions panel
 */
@Composable
private fun QRScannerInstructions(
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "QR code",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Point your camera at a gym buddy's QR code",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (!isProcessing) {
                Text(
                    text = "The QR code will be detected automatically",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Camera permission request screen
 */
@Composable
private fun CameraPermissionRequest(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "Camera",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "To scan QR codes and connect with gym buddies, we need access to your camera.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Allow camera access",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Allow Camera Access")
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun QRScannerScreenPreview() {
    LiftrixTheme {
        QRScannerScreen(
            onQrCodeScanned = { },
            onNavigateBack = { }
        )
    }
} 
