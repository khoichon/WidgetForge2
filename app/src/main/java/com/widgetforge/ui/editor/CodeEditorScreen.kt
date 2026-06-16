package com.widgetforge.ui.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
class CodeEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: WidgetRegistry
) : ViewModel() {

    fun saveBundle(
        widgetId: Int,
        manifest: CodeWidgetManifest,
        mainJs: String,
        assetFiles: List<File>,
        label: String
    ) {
        viewModelScope.launch {
            val dir = File(context.filesDir, "widgets/code")
            dir.mkdirs()
            val name = "widget_${System.currentTimeMillis()}"
            val zipFile = WidgetExportEngine.exportCodeWidget(manifest, mainJs, assetFiles, dir, name)

            val effectiveId = if (widgetId == -1) (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            else widgetId

            registry.register(
                WidgetRegistryEntry(
                    appWidgetId = effectiveId,
                    widgetType = WidgetType.CODE,
                    sourceFilePath = zipFile.absolutePath,
                    label = label,
                    cellWidth = manifest.cellWidth,
                    cellHeight = manifest.cellHeight
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    widgetId: Int,
    onBack: () -> Unit,
    viewModel: CodeEditorViewModel = hiltViewModel()
) {
    var widgetName by remember { mutableStateOf("My Code Widget") }
    var cellW by remember { mutableIntStateOf(2) }
    var cellH by remember { mutableIntStateOf(2) }
    var fps by remember { mutableIntStateOf(30) }
    var mainJsCode by remember { mutableStateOf(DEFAULT_MAIN_JS) }
    var channels by remember { mutableStateOf("") }
    var assetUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var saved by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val assetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> assetUris = uris }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Code Widget Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    Button(
                        onClick = {
                            val channelList = channels.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .map { ChannelConfig(name = it) }

                            val manifest = CodeWidgetManifest(
                                name = widgetName,
                                cellWidth = cellW,
                                cellHeight = cellH,
                                fps = fps,
                                channels = channelList
                            )
                            viewModel.saveBundle(widgetId, manifest, mainJsCode, emptyList(), widgetName)
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
        ) {
            if (saved) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF166534)),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4ADE80))
                        Spacer(Modifier.width(8.dp))
                        Text("Bundle saved! Add it via the launcher widget picker.", color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("main.js") }, icon = { Icon(Icons.Default.Code, null, Modifier.size(16.dp)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Manifest") }, icon = { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("Assets") }, icon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                    text = { Text("Docs") }, icon = { Icon(Icons.Default.Info, null, Modifier.size(16.dp)) })
            }

            when (selectedTab) {
                0 -> CodeEditorTab(mainJsCode) { mainJsCode = it }
                1 -> ManifestTab(widgetName, cellW, cellH, fps, channels,
                    { widgetName = it }, { cellW = it }, { cellH = it }, { fps = it }, { channels = it })
                2 -> AssetsTab(assetUris) { assetLauncher.launch("*/*") }
                3 -> DocsTab()
            }
        }
    }
}

@Composable
private fun CodeEditorTab(code: String, onCodeChange: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("main.js", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            Icon(Icons.Default.Code, null, tint = Color(0xFF6C9EFF), modifier = Modifier.size(16.dp))
        }
        TextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0D1117),
                unfocusedContainerColor = Color(0xFF0D1117),
                focusedTextColor = Color(0xFFE2E8F0),
                unfocusedTextColor = Color(0xFFE2E8F0),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ManifestTab(
    name: String, cellW: Int, cellH: Int, fps: Int, channels: String,
    onName: (String) -> Unit, onW: (Int) -> Unit, onH: (Int) -> Unit,
    onFps: (Int) -> Unit, onChannels: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(value = name, onValueChange = onName,
            label = { Text("Widget Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Text("Widget Size (cells)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Width: $cellW", fontSize = 13.sp)
                Slider(value = cellW.toFloat(), onValueChange = { onW(it.toInt()) }, valueRange = 1f..5f, steps = 3)
            }
            Column(Modifier.weight(1f)) {
                Text("Height: $cellH", fontSize = 13.sp)
                Slider(value = cellH.toFloat(), onValueChange = { onH(it.toInt()) }, valueRange = 1f..5f, steps = 3)
            }
        }

        Text("Frame Rate: ${fps}fps", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Slider(value = fps.toFloat(), onValueChange = { onFps(it.toInt()) }, valueRange = 1f..60f, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(
            value = channels,
            onValueChange = onChannels,
            label = { Text("Event Channels (comma-separated)") },
            placeholder = { Text("weather, timer, score") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Channels defined here allow cross-widget communication") }
        )

        // Generated manifest preview
        val previewJson = """
{
  "name": "$name",
  "version": "1.0.0",
  "cellWidth": $cellW,
  "cellHeight": $cellH,
  "fps": $fps,
  "channels": [${channels.split(",").filter { it.isNotBlank() }.joinToString { "\"${it.trim()}\"" }}]
}""".trimIndent()

        Text("manifest.json Preview", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF0D1117)
        ) {
            Text(
                previewJson,
                modifier = Modifier.padding(12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF4ADE80)
            )
        }
    }
}

@Composable
private fun AssetsTab(assets: List<Uri>, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Assets (${assets.size} files)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text("Add images or fonts referenced in main.js via relative paths like assets/image.png",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))

        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Asset Files")
        }

        assets.forEach { uri ->
            Card(shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(uri.lastPathSegment ?: uri.toString(), fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DocsTab() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("JavaScript API Reference", fontSize = 16.sp, fontWeight = FontWeight.Bold)

        DocSection("Canvas Context", """
Your draw() function receives:
  ctx    — HTML5 Canvas 2D context
  WIDTH  — canvas width in pixels
  HEIGHT — canvas height in pixels
        """.trimIndent())

        DocSection("Required Function", """
function draw(ctx, WIDTH, HEIGHT) {
  // Draw here each frame
  ctx.fillStyle = '#FF6B9D';
  ctx.fillRect(0, 0, WIDTH, HEIGHT);
  ctx.fillStyle = '#FFFFFF';
  ctx.font = '24px sans-serif';
  ctx.fillText('Hello!', WIDTH/2, HEIGHT/2);
}
        """.trimIndent())

        DocSection("Cross-Widget Communication", """
// Publish to a channel
AndroidBridge.publish('weather', JSON.stringify({temp: 22}));

// Receive messages (auto-called by engine)
function onChannelMessage(channel, payload) {
  if (channel === 'weather') {
    console.log('Temp:', payload.temp);
  }
}

// Read current channel state
var state = JSON.parse(AndroidBridge.getChannelState('weather'));
        """.trimIndent())

        DocSection("Lifecycle Hooks", """
// Called when screen turns off
function onWidgetPause() { /* stop heavy calculations */ }

// Called when screen turns on
function onWidgetResume() { /* restart */ }

// Called when widget is resized
function onResize(newWidth, newHeight) {
  WIDTH = newWidth; HEIGHT = newHeight;
}
        """.trimIndent())

        DocSection("Loading Assets", """
// Images placed in assets/ folder:
var img = new Image();
img.src = 'assets/background.png';
img.onload = function() {
  ctx.drawImage(img, 0, 0, WIDTH, HEIGHT);
};
        """.trimIndent())
    }
}

@Composable
private fun DocSection(title: String, code: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF0D1117), modifier = Modifier.fillMaxWidth()) {
            Text(code, modifier = Modifier.padding(12.dp), fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, color = Color(0xFFE2E8F0), lineHeight = 18.sp)
        }
    }
}

private const val DEFAULT_MAIN_JS = """
// WidgetForge Code Widget — main.js
// This function is called every frame at the FPS defined in manifest.json

var hue = 0;

function draw(ctx, WIDTH, HEIGHT) {
  // Animated gradient background
  hue = (hue + 1) % 360;
  var gradient = ctx.createLinearGradient(0, 0, WIDTH, HEIGHT);
  gradient.addColorStop(0, 'hsl(' + hue + ', 70%, 20%)');
  gradient.addColorStop(1, 'hsl(' + (hue + 60) + ', 70%, 10%)');
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, WIDTH, HEIGHT);

  // Clock display
  var now = new Date();
  var time = now.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
  var date = now.toLocaleDateString([], {weekday: 'short', month: 'short', day: 'numeric'});

  ctx.textAlign = 'center';
  ctx.fillStyle = 'rgba(255,255,255,0.9)';
  ctx.font = 'bold ' + (HEIGHT * 0.35) + 'px sans-serif';
  ctx.fillText(time, WIDTH / 2, HEIGHT * 0.55);

  ctx.font = (HEIGHT * 0.15) + 'px sans-serif';
  ctx.fillStyle = 'rgba(255,255,255,0.6)';
  ctx.fillText(date, WIDTH / 2, HEIGHT * 0.8);
}
"""
