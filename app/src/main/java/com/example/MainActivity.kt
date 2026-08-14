package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GenerationState {
    IDLE, LOADING, SUCCESS
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "AI Tailor",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                            )
                        )
                    }
                ) { innerPadding ->
                    VirtualFitScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun VirtualFitScreen(modifier: Modifier = Modifier) {
    var personUri by remember { mutableStateOf<Uri?>(null) }
    var dressUri by remember { mutableStateOf<Uri?>(null) }
    var generationState by remember { mutableStateOf(GenerationState.IDLE) }
    var fitValue by remember { mutableFloatStateOf(0.5f) }
    
    val scope = rememberCoroutineScope()

    val personPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) personUri = uri }

    val dressPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) dressUri = uri }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Section (Result)
        ResultHeroSection(
            state = generationState,
            personUri = personUri,
            dressUri = dressUri,
            fitValue = fitValue
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Input Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ImageSelectorCard(
                modifier = Modifier.weight(1f),
                title = "Person",
                uri = personUri,
                placeholderRes = R.drawable.img_sample_person,
                onClick = {
                    personPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            )

            ImageSelectorCard(
                modifier = Modifier.weight(1f),
                title = "Dress",
                uri = dressUri,
                placeholderRes = R.drawable.img_sample_dress,
                onClick = {
                    dressPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(visible = generationState == GenerationState.SUCCESS) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Adjust Fit precisely",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Slider(
                    value = fitValue,
                    onValueChange = { fitValue = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.tertiary,
                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Loose", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("Tight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            generationState = GenerationState.LOADING
                            delay(1500) // Simulate fast AI adjustment
                            generationState = GenerationState.SUCCESS
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Regenerate Fit")
                }
            }
        }

        // Generate Button
        Button(
            onClick = {
                if (generationState != GenerationState.LOADING) {
                    scope.launch {
                        generationState = GenerationState.LOADING
                        delay(3000) // Simulate AI processing
                        generationState = GenerationState.SUCCESS
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = if (generationState == GenerationState.LOADING) "GENERATING..." else "VIRTUAL TRY-ON",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ResultHeroSection(
    state: GenerationState,
    personUri: Uri?,
    dressUri: Uri?,
    fitValue: Float
) {
    val containerShape = RoundedCornerShape(24.dp)
    var showOriginal by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state == GenerationState.LOADING) {
            showOriginal = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(containerShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
            }, label = "ResultAnimation"
        ) { targetState ->
            when (targetState) {
                GenerationState.IDLE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Your AI Fitting Room",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Upload a person and a dress to see the magic happen in real time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                GenerationState.LOADING -> {
                    ScannerEffect()
                }

                GenerationState.SUCCESS -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Use Coil's AsyncImage for both local drawables and actual user URIs
                        val scale = if (!showOriginal) 0.95f + (fitValue * 0.1f) else 1f
                        val imageModel: Any = if (showOriginal) {
                            personUri ?: R.drawable.img_sample_person
                        } else {
                            R.drawable.img_sample_result
                        }
                        
                        AsyncImage(
                            model = imageModel,
                            contentDescription = if (showOriginal) "Original Image" else "Virtual Try-On Result",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(scale)
                        )
                        
                        // Original vs Result Toggle
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (showOriginal) MaterialTheme.colorScheme.tertiary else Color.Transparent)
                                    .clickable { showOriginal = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Original", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (!showOriginal) MaterialTheme.colorScheme.tertiary else Color.Transparent)
                                    .clickable { showOriginal = false }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Result", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        // Success Badge
                        if (!showOriginal) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("AI Generated", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scanner_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background particles or grid could go here
        
        // Scanner Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.Center)
                .offset(y = 150.dp * offsetY)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
        )
        
        Text(
            text = "AI is analyzing fit...",
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun ImageSelectorCard(
    modifier: Modifier = Modifier,
    title: String,
    uri: Uri?,
    placeholderRes: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Show sample with overlay
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = placeholderRes),
                        contentDescription = "Sample $title",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                    )
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Upload",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                    )
                }
            }
        }
    }
}
