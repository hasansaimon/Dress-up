package com.example

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.*
import com.example.engine.TryOnPipeline
import com.example.ui.EditorScreen
import com.example.ui.HomeScreen
import com.example.ui.PreviewScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val pipeline = remember { TryOnPipeline(context) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var wardrobe by remember { mutableStateOf<List<WardrobeItem>>(emptyList()) }
    var pendingModel by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<EditSession?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pendingModel = uri
        garmentPicker.launch("image/*")
    }
    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { garmentUri ->
        garmentUri ?: return@rememberLauncherForActivityResult
        val model = pendingModel ?: return@rememberLauncherForActivityResult
        pendingModel = null
        busy = true
        scope.launch {
            try {
                val s = withContext(Dispatchers.Default) { pipeline.createSession(model, garmentUri, 0) }
                session = s
                screen = Screen.Editor(s)
            } catch (e: Exception) {
                error = "Pipeline failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }
    val wardrobePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val id = "w${System.currentTimeMillis()}"
                // decode + category guess via pipeline, then append
                val bmp = withContext(Dispatchers.Default) { pipeline.decode(uri) }
                val cat = pipeline.guessCategory(bmp)
                val w = WardrobeItem(id, "Garment ${wardrobe.size + 1}", cat, uri)
                wardrobe = wardrobe + w
            } catch (e: Exception) {
                error = "Wardrobe add failed: ${e.message}"
            }
        }
    }

    fun addLayer(item: WardrobeItem) {
        val s = session ?: return
        scope.launch {
            busy = true
            try {
                val (garment, mask) = withContext(Dispatchers.Default) { pipeline.prepareGarment(item.uri) }
                val assetId = "a${System.nanoTime()}"
                s.assets.put(assetId, garment, mask)
                val layer = GarmentLayer(
                    id = System.nanoTime(),
                    assetId = assetId,
                    name = item.name,
                    category = item.category,
                    zIndex = (s.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
                )
                session = s.copy(layers = s.layers + layer)
                screen = Screen.Editor(session!!)
            } catch (e: Exception) {
                error = "Garment load failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F0F1A)) {
        Box(Modifier.fillMaxSize()) {
            when (val scr = screen) {
                is Screen.Home -> HomeScreen(
                    wardrobe = wardrobe,
                    onNewProject = { modelPicker.launch("image/*") },
                    onAddToWardrobe = { wardrobePicker.launch("image/*") }
                )
                is Screen.Editor -> {
                    val s = scr.session
                    EditorScreen(
                        session = s,
                        wardrobe = wardrobe,
                        onSessionChange = { session = it; screen = Screen.Editor(it) },
                        onAddGarment = { addLayer(it) },
                        onBack = { screen = Screen.Home },
                        onOpenPreview = { after ->
                            screen = Screen.Preview(s.base, after)
                        }
                    )
                }
                is Screen.Preview -> PreviewScreen(
                    before = scr.before,
                    after = scr.after,
                    onBack = { screen = Screen.Editor(session!!) }
                )
            }

            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            error = null
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Editor(val session: EditSession) : Screen
    data class Preview(val before: Bitmap, val after: Bitmap) : Screen
}
