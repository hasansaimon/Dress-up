package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.ChatContext
import com.example.engine.ChatIntentParser
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPanel(
    layers: List<GarmentLayer>,
    selectedId: Long?,
    wardrobe: List<WardrobeItem>,
    parser: ChatIntentParser,
    onCommand: (EditCommand) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(listOf(
            ChatMessage(false, "I'm your edit assistant. Try: \"make the dress red\", \"remove the jacket\", \"add a scarf\", \"make it longer\", \"denim\"."),
        ))
    }
    var thinking by remember { mutableStateOf(false) }

    val quick = listOf("Make it red", "Remove jacket", "Make it longer", "Tighter fit", "Denim fabric", "Brighter", "Show preview")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("Chat Edit", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(min = 160.dp, max = 280.dp).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { m ->
                    val align = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(m.text, Modifier.padding(10.dp))
                        }
                    }
                }
                if (thinking) item { Text("…", style = MaterialTheme.typography.bodyMedium) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                quick.forEach { q ->
                    AssistChip(onClick = { input = q }, label = { Text(q) })
                }
            }

            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Describe an edit…") },
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    enabled = input.isNotBlank() && !thinking,
                    onClick = {
                        val text = input.trim()
                        if (text.isBlank()) return@FilledIconButton
                        messages = messages + ChatMessage(true, text)
                        input = ""
                        thinking = true
                        scope.launch {
                            val (cmds, reply) = parser.parse(text, ChatContext(layers, selectedId, wardrobe))
                            cmds.forEach { onCommand(it) }
                            messages = messages + ChatMessage(false, reply)
                            thinking = false
                            listState.animateScrollToItem(messages.lastIndex)
                        }
                    }
                ) { Text("➤") }
            }
        }
    }
}
