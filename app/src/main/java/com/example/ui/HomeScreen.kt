package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.WardrobeItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    wardrobe: List<WardrobeItem>,
    onNewProject: (Uri) -> Unit,
    onAddToWardrobe: (Uri) -> Unit
) {
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onNewProject)
    }
    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onAddToWardrobe)
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("TryOn Studio", fontWeight = FontWeight.Bold) }) }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GradientActionCard("New Try-On", "Model + garment", listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53))) {
                    modelPicker.launch("image/*")
                }
            }
            item {
                GradientActionCard("Add to Wardrobe", "Upload garments", listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))) {
                    garmentPicker.launch("image/*")
                }
            }

            if (wardrobe.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text("Wardrobe", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(wardrobe) { item ->
                    Card {
                        AsyncImage(model = item.uri, contentDescription = item.name, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f))
                        Text(item.name, Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientActionCard(title: String, subtitle: String, gradient: List<Color>, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick)
            .padding(20.dp)
            .height(150.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}
