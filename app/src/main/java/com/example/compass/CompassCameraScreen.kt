package com.example.compass

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.BottomBarDark
import com.example.ui.theme.BrightGreen
import com.example.ui.theme.DarkGreenOverlay
import com.example.ui.theme.LightGreen
import com.example.ui.theme.MediumGreen
import com.example.ui.theme.NeonGreen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CompassCameraScreen(
    compassSensorManager: CompassSensorManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()

    // Permissions state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    // Compass sensor lifecycle
    DisposableEffect(compassSensorManager) {
        compassSensorManager.startListening()
        onDispose {
            compassSensorManager.stopListening()
        }
    }

    val compassData by compassSensorManager.compassData.collectAsState()

    // 120Hz ultra-smooth frame interpolation
    var renderedAzimuth by remember { mutableFloatStateOf(compassData.azimuth) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos {
                val target = compassData.azimuth
                val diff = (target - renderedAzimuth + 540f) % 360f - 180f
                if (abs(diff) > 0.01f) {
                    // Fluid lerp towards target sensor azimuth at 120 fps
                    renderedAzimuth = (renderedAzimuth + diff * 0.28f + 360f) % 360f
                } else {
                    renderedAzimuth = target
                }
            }
        }
    }

    // CameraX components
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: Camera? by remember { mutableStateOf(null) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }

    // Shutter animation and status message
    val shutterAlpha = remember { Animatable(0f) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var lastSavedUri by remember { mutableStateOf<Uri?>(null) }

    // Top inset below punch-hole (Samsung S21: ~48-56dp)
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPaddingDp = if (statusBarPadding < 48.dp) 52.dp else statusBarPadding + 8.dp
    val topPaddingPx = with(LocalDensity.current) { topPaddingDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            // 1. Fullscreen Camera View (filling 20:9 display)
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

                        val preview = Preview.Builder()
                            .setTargetRotation(rotation)
                            .build()
                            .also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setFlashMode(flashMode)
                            .setTargetRotation(rotation)
                            .build()
                        imageCapture = capture

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetRotation(rotation)
                            .build()

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraControl = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture,
                                analysis
                            )
                        } catch (exc: Exception) {
                            // Camera bind fallback
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_preview_view")
            )

            // 2. HUD Overlays (Central vertical green line + Compass graduated ruler)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("compass_hud_canvas")
            ) {
                drawIntoCanvas { canvas ->
                    CompassOverlayDrawer.drawOverlay(
                        canvas = canvas.nativeCanvas,
                        width = size.width,
                        height = size.height,
                        azimuth = renderedAzimuth,
                        density = density,
                        topPaddingPx = topPaddingPx,
                        isCapture = false
                    )
                }
            }

            // 3. Telemetry Info Strip (Pitch, Roll & Status badge)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topPaddingDp + 116.dp, end = 20.dp)
                    .background(DarkGreenOverlay, RoundedCornerShape(8.dp))
                    .border(1.dp, MediumGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PITCH: %+.1f°".format(compassData.pitch),
                        color = LightGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ROLL:  %+.1f°".format(compassData.roll),
                        color = LightGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4. White shutter flash effect on capture
            if (shutterAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = shutterAlpha.value))
                )
            }

            // 5. Toast / HUD Snackbar ("Imagen guardada")
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPaddingDp + 120.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(BottomBarDark, RoundedCornerShape(12.dp))
                        .border(1.5.dp, BrightGreen, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = BrightGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = statusMessage ?: "",
                            color = NeonGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 6. Bottom Bar with Capture Button moved UP with solid black footer
            // (EXCLUDED from the final image because it is not drawn into the capture Bitmap)
            val navBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomBlackHeight = if (navBarsPadding > 0.dp) navBarsPadding else 48.dp

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                // Action Buttons Toolbar elevated above device navigation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(BottomBarDark)
                        .border(
                            width = 1.dp,
                            color = MediumGreen.copy(alpha = 0.35f)
                        )
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flash / Torch toggle
                        IconButton(
                            onClick = {
                                val nextMode = when (flashMode) {
                                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                    else -> ImageCapture.FLASH_MODE_OFF
                                }
                                flashMode = nextMode
                                imageCapture?.flashMode = nextMode
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("flash_toggle_button")
                        ) {
                            val iconRes = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
                                ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
                                else -> R.drawable.ic_flash_off
                            }
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = "Flash mode",
                                tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) NeonGreen else LightGreen.copy(alpha = 0.6f),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Centered circular capture button (64dp, green with camera icon)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(BrightGreen)
                                .border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                .clickable(enabled = !isCapturing && imageCapture != null) {
                                    val currentCapture = imageCapture ?: return@clickable
                                    isCapturing = true

                                    // Trigger shutter flash animation
                                    coroutineScope.launch {
                                        shutterAlpha.snapTo(0.85f)
                                        shutterAlpha.animateTo(0f, tween(180))
                                    }

                                    CaptureManager.capturePhotoWithCompassOverlay(
                                        context = context,
                                        imageCapture = currentCapture,
                                        executor = cameraExecutor,
                                        currentAzimuth = renderedAzimuth
                                    ) { result ->
                                        isCapturing = false
                                        when (result) {
                                            is CaptureManager.CaptureResult.Success -> {
                                                lastSavedUri = result.uri
                                                statusMessage = context.getString(R.string.image_saved)
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(2600)
                                                    statusMessage = null
                                                }
                                            }
                                            is CaptureManager.CaptureResult.Error -> {
                                                statusMessage = context.getString(R.string.save_failed)
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(3000)
                                                    statusMessage = null
                                                }
                                            }
                                        }
                                    }
                                }
                                .testTag("capture_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_camera),
                                    contentDescription = stringResource(R.string.capture_image),
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Gallery thumbnail / view last photo button
                        IconButton(
                            onClick = {
                                val uri = lastSavedUri
                                if (uri != null) {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "image/*")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    try {
                                        context.startActivity(viewIntent)
                                    } catch (e: Exception) {
                                        // Fallback to gallery intent
                                        val galleryIntent = Intent(Intent.ACTION_VIEW, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                        context.startActivity(galleryIntent)
                                    }
                                } else {
                                    val galleryIntent = Intent(Intent.ACTION_VIEW, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                    try {
                                        context.startActivity(galleryIntent)
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("gallery_button")
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_gallery),
                                contentDescription = "Gallery",
                                tint = if (lastSavedUri != null) NeonGreen else LightGreen.copy(alpha = 0.5f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Dedicated solid pure black spacer for the Android system navigation bar
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomBlackHeight)
                        .background(Color.Black)
                )
            }

        } else {
            // Permission request screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Permiso de Cámara y Ubicación Requeridos",
                    color = NeonGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.permission_camera_rationale),
                    color = LightGreen,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_location_rationale),
                    color = LightGreen.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrightGreen,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("grant_permission_button")
                ) {
                    Text(
                        text = stringResource(R.string.grant_permissions),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
