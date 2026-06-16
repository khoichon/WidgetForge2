package com.widgetforge.ui.editor

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.widgetforge.data.models.*
import com.widgetforge.data.repository.WidgetRegistry
import com.widgetforge.export.WidgetExportEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TextEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: WidgetRegistry
) : ViewModel() {

    fun saveTextWidget(
        widgetId: Int,
        config: TextWidgetConfig,
        label: String,
        cellW: Int,
        cellH: Int
    ) {
        viewModelScope.launch {
            val dir = File(context.filesDir, "widgets/text")
            dir.mkdirs()
            val file = File(dir, "widget_${System.currentTimeMillis()}.txt")
            WidgetExportEngine.exportText(config, dir, "widget_${System.currentTimeMillis()}")

            val effectiveId = if (widgetId == -1) {
                // When not coming from AppWidget system, store as standalone
                (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            } else widgetId

            val entry = WidgetRegistryEntry(
                appWidgetId = effectiveId,
                widgetType = WidgetType.TEXT,
                sourceFilePath = file.absolutePath,
                label = label,
                cellWidth = cellW,
                cellHeight = cellH
            )
            registry.register(entry)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    widgetId: Int,
    onBack: () -> Unit,
    viewModel: TextEditorViewModel = hiltViewModel()
) {
    var text by remember { mutableStateOf("Hello, World!") }
    var label by remember { mutableStateOf("My Text Widget") }
    var fontSize by remember { mutableStateOf(16f) }
    var textColor by remember { mutableStateOf("#FFFFFF") }
    var bgColor by remember { mutableStateOf("#CC000000") }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var alignment by remember { mutableStateOf(TextAlignment.CENTER) }
    var cellW by remember { mutableIntStateOf(2) }
    var cellH by remember { mutableIntStateOf(2) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text Widget", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val config = TextWidgetConfig(
                                text = text,
                                fontSize = fontSize,
                                textColor = textColor,
                                backgroundColor = bgColor,
                                alignment = when (alignment) {
                                    TextAlignment.LEFT -> com.widgetforge.data.models.TextAlignment.LEFT
                                    TextAlignment.RIGHT -> com.widgetforge.data.models.TextAlignment.RIGHT
                                    else -> com.widgetforge.data.models.TextAlignment.CENTER
                                },
                                bold = bold,
                                italic = italic
                            )
                            viewModel.saveTextWidget(widgetId, config, label, cellW, cellH)
                            saved = true
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (saved) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF166534)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4ADE80))
                        Spacer(Modifier.width(8.dp))
                        Text("Widget saved! Add it from your launcher's widget picker.", color = Color.White)
                    }
                }
            }

            // Live preview
            SectionLabel("Preview")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((cellH * 74 + 32).dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(parsePreviewColor(bgColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text.ifEmpty { "Enter text below" },
                    color = parsePreviewColor(textColor),
                    fontSize = fontSize.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = when (alignment) {
		        Alignment.Start, Alignment.TopStart, Alignment.BottomStart -> TextAlign.Start
			Alignment.End, Alignment.TopEnd, Alignment.BottomEnd -> TextAlign.End
			else -> TextAlign.Center
	    }
                )
            }

            // Widget label
            SectionLabel("Widget Label")
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (for your reference)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Text content
            SectionLabel("Content")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Widget text") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                maxLines = 5
            )

            // Font size
            SectionLabel("Font Size: ${fontSize.toInt()}sp")
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                valueRange = 8f..48f,
                modifier = Modifier.fillMaxWidth()
            )

            // Style toggles
            SectionLabel("Style")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = bold,
                    onClick = { bold = !bold },
                    label = { Text("Bold", fontWeight = FontWeight.Bold) },
                    leadingIcon = if (bold) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = italic,
                    onClick = { italic = !italic },
                    label = { Text("Italic") },
                    leadingIcon = if (italic) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }

            // Text alignment
            SectionLabel("Alignment")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TextAlignment.LEFT to Icons.Default.FormatAlignLeft,
                    TextAlignment.CENTER to Icons.Default.FormatAlignCenter,
                    TextAlignment.RIGHT to Icons.Default.FormatAlignRight
                ).forEach { (align, icon) ->
                    FilterChip(
                        selected = alignment == align,
                        onClick = { alignment = align },
                        label = { Icon(icon, null, Modifier.size(18.dp)) }
                    )
                }
            }

            // Colors
            SectionLabel("Colors")
            ColorInput("Text Color", textColor) { textColor = it }
            Spacer(Modifier.height(4.dp))
            ColorInput("Background", bgColor) { bgColor = it }

            // Grid size
            SectionLabel("Widget Size (cells)")
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Width: $cellW", fontSize = 13.sp)
                    Slider(value = cellW.toFloat(), onValueChange = { cellW = it.toInt() }, valueRange = 1f..5f, steps = 3)
                }
                Column(Modifier.weight(1f)) {
                    Text("Height: $cellH", fontSize = 13.sp)
                    Slider(value = cellH.toFloat(), onValueChange = { cellH = it.toInt() }, valueRange = 1f..5f, steps = 3)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ColorInput(label: String, value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(parsePreviewColor(value))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

private fun parsePreviewColor(hex: String): Color {
    return try {
        val c = android.graphics.Color.parseColor(hex)
        Color(c)
    } catch (e: Exception) {
        Color.Black
    }
}
