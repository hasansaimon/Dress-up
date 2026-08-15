package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.CompositeEngine
import com.example.engine.RuleBasedParser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    session: EditSession,
    wardrobe: List<WardrobeItem>,
    onSessionChange: (EditSession) -> Unit,
    onAddGarment: (WardrobeItem) -> Unit,
    onBack: () -> Unit,
    onOpenPreview: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { EditHistory<List<GarmentLayer>>() }

    var layers by remember { mutableStateOf(session.layers) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showLayers by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var maskEditing by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(session) { layers = session.layers }

    val rendered by produceState(initialValue = session.base, key1 = layers) {
        value = CompositeEngine.render(session.base, layers, session.assets)
    }

    fun commit(newLayers: List<GarmentLayer>) {
        history.push(layers)
        layers = newLayers
        onSessionChange(session.copy(layers = newLayers))
    }

    fun apply(cmd: EditCommand) {
        when (cmd) {
            is EditCommand.Undo -> history.undo()?.let {
                layers = it; onSessionChange(session.copy(layers = it))
            }
            is EditCommand.AddGarment ->
                wardrobe.firstOrNull { it.id == cmd.assetId }?.let(onAddGarment)
            is EditCommand.Remove -> commit(layers.filterNot { it.id == cmd.layerId })
            is EditCommand.Duplicate -> {
                val src = layers.firstOrNull { it.id == cmd.layerId } ?: return
                commit(layers + src.copy(id = System.nanoTime(), zIndex = layers.maxOf { it.zIndex } + 1))
            }
            is EditCommand.ToggleVisible -> commit(layers.map { if (it.id == cmd.layerId) it.copy(visible = !it.visible) else it })
            is EditCommand.Move -> commit(layers.map { if (it.id == cmd.layerId) it.copy(offset = it.offset + cmd.delta) else it })
            is EditCommand.Resize -> commit(layers.map { if (it.id == cmd.layerId) it.copy(scaleX = it.scaleX * cmd.sx, scaleY = it.scaleY * cmd.sy) else it })
            is EditCommand.Rotate -> commit(layers.map { if (it.id == cmd.layerId) it.copy(rotationDeg = it.rotationDeg + cmd.degrees) else it })
            is EditCommand.SetOpacity -> commit(layers.map { if (it.id == cmd.layerId) it.copy(opacity = cmd.value) else it })
            is EditCommand.ChangeColor -> commit(layers.map { if (it.id == cmd.layerId) it.copy(hueShift = cmd.hue, saturation = cmd.saturation) else it })
            is EditCommand.SetBrightnessContrast -> commit(layers.map { if (it.id == cmd.layerId) it.copy(brightness = cmd.brightness, contrast = cmd.contrast) else it })
            is EditCommand.SetFabric -> commit(layers.map { if (it.id == cmd.layerId) it.copy(fabric = cmd.fabric) else it })
            is EditCommand.Preview -> onOpenPreview(rendered)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Try-On Studio") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "back") } },
                actions = {
                    IconButton(onClick = { showChat = !showChat }) { Icon(Icons.Default.Chat, "chat edit") }
                    IconButton(onClick = { onOpenPreview(rendered) }) { Icon(Icons.Default.CompareArrows, "compare") }
                    IconButton(onClick = { CompositeEngine.export(context, rendered, "tryon_${System.currentTimeMillis()}") }) { Icon(Icons.Default.Share, "export") }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToolIcon(Icons.Default.Undo, "Undo", history.canUndo()) { apply(EditCommand.Undo) }
                    ToolIcon(Icons.Default.Layers, "Layers", true) { showLayers = !showLayers }
                    ToolIcon(Icons.Default.Add, "Add", true) { wardrobe.firstOrNull()?.let(onAddGarment) }
                    ToolIcon(Icons.Default.Delete, "Remove", selectedId != null) {
                        selectedId?.let { apply(EditCommand.Remove(it)); selectedId = null }
                    }
                    ToolIcon(Icons.Default.Tune, "Adjust", selectedId != null) { showAdjust = !showAdjust }
                    ToolIcon(Icons.Default.Brush, "Mask", selectedId != null) { maskEditing = if (maskEditing == selectedId) null else selectedId }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            EditorCanvas(
                session = session, layers = layers, selectedId = selectedId,
                onSelect = { selectedId = it },
                onTransform = { id, delta, sx, sy, deg ->
                    commit(layers.map {
                        if (it.id == id) it.copy(offset = it.offset + delta, scaleX = it.scaleX * sx, scaleY = it.scaleY * sy, rotationDeg = it.rotationDeg + deg) else it
                    })
                },
                modifier = Modifier.weight(1f)
            )
            if (showAdjust) {
                val sel = layers.firstOrNull { it.id == selectedId } ?: return@Column
                AdjustPanel(layer = sel, onUpdate = { copy -> commit(layers.map { if (it.id == sel.id) copy else it }) })
            }
        }
    }

    if (showLayers) {
        ModalBottomSheet(onDismissRequest = { showLayers = false }) {
            LazyColumn(Modifier.heightIn(max = 360.dp).padding(bottom = 24.dp)) {
                items(layers.sortedByDescending { it.zIndex }, key = { it.id }) { l ->
                    ListItem(
                        headlineContent = { Text(l.name) },
                        supportingContent = { Text("${l.category} · ${if (l.visible) "visible" else "hidden"}") },
                        leadingContent = { Icon(Icons.Default.Checkroom, null) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { apply(EditCommand.ToggleVisible(l.id)) }) {
                                    Icon(if (l.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "toggle")
                                }
                                IconButton(onClick = { selectedId = l.id; apply(EditCommand.Duplicate(l.id)) }) { Icon(Icons.Default.ContentCopy, "duplicate") }
                                IconButton(onClick = { apply(EditCommand.Remove(l.id)) }) { Icon(Icons.Default.Delete, "delete") }
                            }
                        },
                        modifier = Modifier.clickable { selectedId = l.id }
                    )
                }
            }
        }
    }

    if (showChat) {
        ChatPanel(
            layers = layers, selectedId = selectedId, wardrobe = wardrobe,
            parser = RuleBasedParser(), onCommand = { apply(it) }, onDismiss = { showChat = false }
        )
    }

    maskEditing?.let { id ->
        val layer = layers.firstOrNull { it.id == id } ?: return@let
        val (garment, mask) = session.assets.get(layer.assetId) ?: return@let
        if (mask != null) {
            MaskBrushSheet(
                mask = mask,
                onDone = { newMask ->
                    session.assets.put(layer.assetId, garment, newMask)
                    maskEditing = null
                    onSessionChange(session)
                },
                onDismiss = { maskEditing = null }
            )
        }
    }
}

@Composable
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = enabled, onClick = onClick)) {
        Icon(icon, label, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EditorCanvas(
    session: EditSession, layers: List<GarmentLayer>, selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onTransform: (Long, Offset, Float, Float, Float) -> Unit,
    modifier: Modifier
) {
    var viewportScale by remember { mutableFloatStateOf(1f) }
    var viewportPan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val rendered by produceState(initialValue = session.base, key1 = layers) {
        value = CompositeEngine.render(session.base, layers, session.assets)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { canvasSize = it }
            .pointerInput(selectedId) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    val sel = selectedId
                    if (sel == null) {
                        viewportScale = (viewportScale * zoom).coerceIn(0.5f, 8f)
                        viewportPan += pan
                    } else onTransform(sel, pan, zoom, zoom, rotation)
                }
            }
            .pointerInput(selectedId) {
                detectTapGestures { pos ->
                    val sel = layers.lastOrNull { l -> hitTest(l, pos, canvasSize) }
                    onSelect(sel?.id)
                }
            }
    ) {
        Image(
            bitmap = rendered.asImageBitmap(), contentDescription = "canvas",
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = viewportScale; scaleY = viewportScale
                translationX = viewportPan.x; translationY = viewportPan.y
            }
        )
        selectedId?.let { id ->
            val l = layers.firstOrNull { it.id == id } ?: return@let
            val w = canvasSize.width * 0.25f * l.scaleX
            val h = canvasSize.height * 0.4f * l.scaleY
            val cx = canvasSize.width / 2f + l.offset.x
            val cy = canvasSize.height / 2f + l.offset.y
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = androidx.compose.ui.graphics.Color.White,
                    topLeft = Offset(cx - w / 2, cy - h / 2),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                )
            }
        }
    }
}

private fun hitTest(l: GarmentLayer, pos: Offset, size: IntSize): Boolean {
    val w = size.width * 0.25f * l.scaleX
    val h = size.height * 0.4f * l.scaleY
    val cx = size.width / 2f + l.offset.x
    val cy = size.height / 2f + l.offset.y
    return pos.x in (cx - w / 2)..(cx + w / 2) && pos.y in (cy - h / 2)..(cy + h / 2)
}

@Composable
private fun AdjustPanel(layer: GarmentLayer, onUpdate: (GarmentLayer) -> Unit) {
    Surface(tonalElevation = 6.dp) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SliderRow("Opacity", layer.opacity, 0f..1f) { onUpdate(layer.copy(opacity = it)) }
            SliderRow("Width", layer.scaleX, 0.5f..2f) { onUpdate(layer.copy(scaleX = it)) }
            SliderRow("Length", layer.scaleY, 0.5f..2f) { onUpdate(layer.copy(scaleY = it)) }
            SliderRow("Rotate", layer.rotationDeg, -180f..180f) { onUpdate(layer.copy(rotationDeg = it)) }
            SliderRow("Hue", layer.hueShift, 0f..360f) { onUpdate(layer.copy(hueShift = it)) }
            SliderRow("Saturation", layer.saturation, 0f..2f) { onUpdate(layer.copy(saturation = it)) }
            SliderRow("Brightness", layer.brightness, -100f..100f) { onUpdate(layer.copy(brightness = it)) }
            SliderRow("Contrast", layer.contrast, 0.5f..2f) { onUpdate(layer.copy(contrast = it)) }
            Text("Fabric", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FabricStyle.entries.forEach { f ->
                    FilterChip(selected = layer.fabric == f, onClick = { onUpdate(layer.copy(fabric = f))) },
                        label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label  ", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(110.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaskBrushSheet(mask: Bitmap, onDone: (Bitmap) -> Unit, onDismiss: () -> Unit) {
    var work by remember { mutableStateOf(mask.copy(Bitmap.Config.ARGB_8888, true)) }
    var erase by remember { mutableStateOf(false) }
    var imgSize by remember { mutableStateOf(IntSize.Zero) }
    var version by remember { mutableIntStateOf(0) }

    fun paint(pos: Offset) {
        val sx = work.width / imgSize.width.toFloat()
        val sy = work.height / imgSize.height.toFloat()
        val c = android.graphics.Canvas(work)
        val p = android.graphics.Paint().apply {
            color = if (erase) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        c.drawCircle(pos.x * sx, pos.y * sy, 42f * sx, p)
        version++
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Refine mask — drag to paint", style = MaterialTheme.typography.titleSmall)
            Row {
                FilterChip(selected = !erase, onClick = { erase = false }, label = { Text("Add") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = erase, onClick = { erase = true }, label = { Text("Erase") })
            }
            val display = remember(version) { work.asImageBitmap() }
            Image(
                bitmap = display, contentDescription = "mask",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(work.width.toFloat() / work.height)
                    .onSizeChanged { imgSize = it }
                    .pointerInput(Unit) { detectTapGestures { paint(it) } }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { paint(it) },
                            onDrag = { change, _ -> paint(change.position) },
                            onDragEnd = {}, onDragCancel = {}
                        )
                    }
            )
            Button(onClick = { onDone(work) }, modifier = Modifier.fillMaxWidth()) { Text("Apply mask") }
        }
    }
}
