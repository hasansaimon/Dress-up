package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.engine.CompositeEngine

/** Before/after slider + HD export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(before: Bitmap, after: Bitmap, onBack: () -> Unit) {
    val context = LocalContext.current
    var split by remember { mutableFloatStateOf(0.5f) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Preview") }, navigationIcon = { IconButton(onClick = onBack) { Text("‹") } }) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Drag to compare", style = MaterialTheme.typography.labelMedium)

            Box(Modifier.fillMaxWidth().aspectRatio(before.width.toFloat() / before.height)) {
                Image(before.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Image(
                    after.asImageBitmap(), null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.drawWithContent {
                        clipRect(right = size.width * split) { this@drawWithContent.drawContent() }
                    }
                )
            }
            Slider(value = split, onValueChange = { split = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back to edit") }
                Button(onClick = { CompositeEngine.export(context, after, "tryon_final") }, modifier = Modifier.weight(1f)) { Text("Save HD") }
            }
        }
    }
}
