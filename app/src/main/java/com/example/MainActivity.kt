package com.example

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.data.*
import com.example.engine.TryOnPipeline
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Editor : Screen
    data object Preview : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MyApplicationTheme { App() } }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val pipeline = remember { TryOnPipeline(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var session by remember { mutableStateOf<EditSession?>(null) }
    var wardrobe by remember { mutableStateOf<List<WardrobeItem>>(emptyList()) }
    var previewPair by remember { mutableStateOf<Pair<Bitmap, Bitmap>?>(null) }
    var pendingModel by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // --- pickers ----------------------------------------------------------
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingModel = uri
    }

    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        when (screen) {
            is Screen.Home -> {
                // model selected earlier -> create session -> open editor
                val model = pendingModel
                pendingModel = null
                if (model == null) {
                    // no model yet: just add to wardrobe
                    wardrobe = wardrobe + WardrobeItem(
                        id = "w_${System.nanoTime()}", name = "Item ${wardrobe.size + 1}",
                        category = GarmentCategory.UNDEFINED, uri = uri
                    )
                } else {
                    scope.launch {
                        busy = true; error = null
                        session = try {
                            withContext(Dispatchers.Default) { pipeline.createSession(model, uri, 0) }
                        } catch (e: Exception) { error = e.message; null }
                        busy = false
                        if (session != null) screen = Screen.Editor
                    }
                }
            }
            is Screen.Editor -> addLayer(WardrobeItem(
                id = "tmp_${System.nanoTime()}", name = "New garment",
                category = GarmentCategory.UNDEFINED, uri = uri
            ))
            is Screen.Preview -> {}
        }
    }

    // --- actions ----------------------------------------------------------
    fun addLayer(item: WardrobeItem) {
        val s = session ?: return
        scope.launch {
            try {
                val (bmp, mask) = withContext(Dispatchers.Default) { pipeline.prepareGarment(item.uri) }
                val assetId = "layer_${System.nanoTime()}"
                s.assets.put(assetId, bmp, mask)
                val layer = GarmentLayer(
                    id = System.nanoTime(), assetId = assetId, name = item.name,
                    category = item.category, zIndex = (s.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
                )
                session = s.copy(layers = s.layers + layer)
            } catch (e: Exception) { error = e.message }
        }
    }

    // --- screens ----------------------------------------------------------
    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            wardrobe = wardrobe,
            onNewProject = { modelPicker.launch("image/*") },
            onAddToWardrobe = { garmentPicker.launch("image/*") }
        )

        is Screen.Editor -> session?.let { sess ->
            EditorScreen(
                session = sess,
                wardrobe = wardrobe,
                onSessionChange = { session = it },
                onAddGarment = { addLayer(it) },
                onBack = { screen = Screen.Home },
                onOpenPreview = { after -> previewPair = sess.base to after; screen = Screen.Preview }
            )
        }

        is Screen.Preview -> previewPair?.let { (b, a) ->
            PreviewScreen(before = b, after = a, onBack = { screen = Screen.Editor })
        }
    }

    if (busy) {
        // lightweight processing indicator
        androidx.compose.material3.Surface(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)
        ) { androidx.compose.material3.CircularProgressIndicator(androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.Center)) }
    }
    error?.let { msg ->
        androidx.compose.material3.SnackbarHost(
            hostState = remember { androidx.compose.material3.SnackbarHostState() }.also {
                androidx.compose.runtime.LaunchedEffect(msg) { it.showSnackbar(msg) }
            }
        )
    }
}
