package app.getkosha.add

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.getkosha.design.Accent
import app.getkosha.design.Button
import app.getkosha.design.ButtonStyle
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaColors
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Receipts go to the AI at most this size on the long side (ai.tsx shrink). */
private const val LONG_SIDE = 1600

/**
 * Scan a receipt (ReceiptCamera): a full-screen camera with a receipt frame, the shutter, and Choose a photo. The
 * photo is shrunk to 1600px, read by AI into the form and attached once the entry is saved. Without camera
 * permission it explains and still offers a photo.
 */
@Composable
internal fun ReceiptCamera(m: AddModel, ui: AddUi) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val light = KoshaTheme.colors
    // A viewfinder is dark whatever the theme: Kosha's dark tokens, with the person's accent.
    val c = remember(light.brand, light.neutral) {
        KoshaColors.of(dark = true, accent = Accent.entries.firstOrNull { it.name.equals(m.meta.pref("accent", "teal"), true) } ?: Accent.Teal, neutral = light.neutral)
    }
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            bars?.isAppearanceLightStatusBars = !light.dark
            bars?.isAppearanceLightNavigationBars = !light.dark
        }
    }

    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val send = { jpeg: ByteArray? ->
        if (jpeg == null) ui.error(ui.s(R.string.add_error_receipt)) else m.scan(ui, jpeg)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { send(withContext(Dispatchers.Default) { runCatching { shrink(context, uri) }.getOrNull() }) }
    }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var flash by remember { mutableStateOf(false) }
    capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    val reading = m.reading == Filled.Receipt

    Box(Modifier.fillMaxSize().background(c.bg).clickable(interactionSource = null, indication = null) { }) {
        if (granted) {
            val preview = remember { Preview.Builder().build() }
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { pv ->
                        preview.setSurfaceProvider(pv.surfaceProvider)
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            runCatching {
                                future.get().apply {
                                    unbind(preview, capture)
                                    bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                }
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(Unit) {
                onDispose {
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({ runCatching { future.get().unbind(preview, capture) } }, ContextCompat.getMainExecutor(context))
                }
            }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton("x", stringResource(R.string.common_close), { m.camera = false }, tint = c.ink)
                KText(stringResource(R.string.add_scan_receipt), Modifier.weight(1f), style = KoshaTheme.type.head.copy(fontSize = KoshaTheme.type.button.fontSize), color = c.ink, align = TextAlign.Center)
                if (granted) {
                    IconButton(
                        if (flash) "lightning" else "lightning-slash",
                        stringResource(if (flash) R.string.app_add_flash_on else R.string.app_add_flash_off),
                        { flash = !flash },
                        tint = c.ink,
                    )
                } else Spacer(Modifier.size(48.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (granted) {
                    Frame(c.ink)
                } else {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PhIcon("camera-slash", size = 32.dp, tint = c.ink2)
                        KText(stringResource(R.string.app_add_camera_needed), color = c.ink2, align = TextAlign.Center)
                        Button(stringResource(R.string.app_add_allow_camera), {
                            val activity = context as? Activity
                            // Asked and refused for good: only Settings can turn it on now.
                            if (asked && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false) {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            } else permission.launch(Manifest.permission.CAMERA)
                        }, style = ButtonStyle.Soft)
                    }
                }
                if (reading) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.soft)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spinner(tint = c.brand)
                        KText(stringResource(R.string.add_hint_reading_receipt), color = c.ink)
                    }
                }
            }
            if (granted) {
                KText(stringResource(R.string.app_add_camera_hint), Modifier.fillMaxWidth().padding(16.dp), style = KoshaTheme.type.small, color = c.ink2, align = TextAlign.Center)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pick = stringResource(R.string.app_add_choose_photo)
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.soft)
                        .clickable(enabled = !reading, role = Role.Button, onClickLabel = pick) {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .semantics { contentDescription = pick },
                    contentAlignment = Alignment.Center,
                ) { PhIcon("images", size = 24.dp, tint = c.ink) }
                if (granted) {
                    val shoot = stringResource(R.string.app_add_take_photo)
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(4.dp, c.ink, CircleShape)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(c.ink)
                            .clickable(enabled = !reading, role = Role.Button, onClickLabel = shoot) {
                                capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        scope.launch { send(withContext(Dispatchers.Default) { runCatching { shrink(image) }.getOrNull() }) }
                                    }

                                    override fun onError(exception: ImageCaptureException) = send(null)
                                })
                            }
                            .semantics { contentDescription = shoot },
                    )
                }
                // ponytail: no PDF button; read_transaction reads JPEG, PNG and WebP photos only.
                Spacer(Modifier.size(56.dp))
            }
        }
    }
}

/** The receipt frame: four corner brackets. */
@Composable
private fun Frame(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth(0.76f)
            .aspectRatio(0.64f)
            .drawBehind {
                val w = 3.dp.toPx()
                val arm = 28.dp.toPx()
                val corners = listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f),
                    Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f))
                corners.forEach { (p, d) ->
                    drawLine(color, p, Offset(p.x + d.x * arm, p.y), w, StrokeCap.Round)
                    drawLine(color, p, Offset(p.x, p.y + d.y * arm), w, StrokeCap.Round)
                }
            },
    )
}

/** A captured frame, turned upright and shrunk, as JPEG. */
private fun shrink(image: ImageProxy): ByteArray = image.use {
    val bitmap = it.toBitmap()
    val scale = min(1f, LONG_SIDE.toFloat() / max(bitmap.width, bitmap.height))
    val matrix = Matrix().apply {
        postRotate(it.imageInfo.rotationDegrees.toFloat())
        postScale(scale, scale)
    }
    jpeg(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true))
}

/** A picked photo, shrunk while it decodes (ImageDecoder also turns it upright). */
private fun shrink(context: Context, uri: Uri): ByteArray {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val scale = min(1f, LONG_SIDE.toFloat() / max(info.size.width, info.size.height))
        decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    return jpeg(bitmap)
}

private fun jpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
