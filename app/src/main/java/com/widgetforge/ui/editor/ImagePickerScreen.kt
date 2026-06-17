package com.widgetforge.ui.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.widgetforge.data.models.*
import com.widgetforge.data.repository.WidgetRegistry
import com.widgetforge.export.WidgetExportEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ImagePickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: WidgetRegistry
) : ViewModel() {

    fun saveTemplate(
        uri: Uri, label: String, type: WidgetType,
        cellW: Int, cellH: Int, cornerRadius: Float, onClickAction: String
    ) {
        viewModelScope.launch {
            val ext  = if (type == WidgetType.GIF) "gif" else "png"
            val dir  = File(context.filesDir, "widgets/src").also { it.mkdirs() }
            val src  = File(dir, "src_${System.currentTimeMillis()}.$ext")
            // Copy original from content URI
            context.contentResolver.openInputStream(uri)?.use { inp ->
                src.outputStream().use { out -> inp.copyTo(out) }
            }

            val outDir = File(context.filesDir, "widgets/${type.name.lowercase()}").also { it.mkdirs() }
            val name   = "template_${System.currentTimeMillis()}"

            val destFile = if (type == WidgetType.GIF) {
                WidgetExportEngine.exportGif(
                    GifWidgetConfig(
                        gifPath = src.absolutePath, cornerRadius = cornerRadius,
                        label = label, onClickAction = onClickAction,
                        cellWidth = cellW, cellHeight = cellH
                    ), outDir, name
                )
            } else {
                WidgetExportEngine.exportImage(
                    ImageWidgetConfig(
                        imagePath = src.absolutePath, cornerRadius = cornerRadius,
                        label = label, onClickAction = onClickAction,
                        cellWidth = cellW, cellHeight = cellH
                    ), outDir, name
                )
            }

            registry.saveTemplate(
                WidgetTemplate(
                    widgetType     = type,
                    sourceFilePath = destFile.absolutePath,
                    label          = label,
                    cellWidth      = cellW,
                    cellHeight     = cellH,
                    onClickAction  = onClickAction
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerScreen(
    widgetId: Int,
    type: String,
    onBack: () -> Unit,
    viewModel: ImagePickerViewModel = hiltViewModel()
) {
    val context       = LocalContext.current
    val widgetType    = if (type == "GIF") WidgetType.GIF else WidgetType.IMAGE
    var selectedUri   by remember { mutableStateOf<Uri?>(null) }
    var label         by remember { mutableStateOf("My ${if (type == "GIF") "GIF" else "Image"} Widget") }
    var cellW         by remember { mutableIntStateOf(2) }
    var cellH         by remember { mutableIntStateOf(2) }
    var cornerRadius  by remember { mutableStateOf(12f) }
    var onClickAction by remember { mutableStateOf("") }
    var saved         by remember { mutableStateOf(false) }

    val mimeType = if (type == "GIF") "image/gif" else "image/*"
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { selectedUri = it } }

    val gifLoader = remember {
        ImageLoader.Builder(context).components { add(GifDecoder.Factory()) }.build()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (type == "GIF") "Animated GIF Widget" else "Image Widget",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (selectedUri != null) {
                        Button(
                            onClick = {
                                viewModel.saveTemplate(
                                    selectedUri!!, label, widgetType,
                                    cellW, cellH, cornerRadius, onClickAction
                                )
                                saved = true
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Template")
                        }
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
            if (saved) SavedBanner("Template saved! Go to widget picker to place it.")

            // Preview
            Card(modifier = Modifier.fillMaxWidth().height(200.dp),
                shape = RoundedCornerShape(16.dp)) {
                if (selectedUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(selectedUri).build(),
                        imageLoader = if (type == "GIF") gifLoader else ImageLoader(context),
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadius.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (type == "GIF") Icons.Default.Gif else Icons.Default.Image,
                                null, modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No file selected",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        }
                    }
                }
            }

            OutlinedButton(onClick = { launcher.launch(mimeType) },
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose ${if (type == "GIF") "GIF" else "Image"} File")
            }

            OutlinedTextField(value = label, onValueChange = { label = it },
                label = { Text("Template Label") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            SectionLabel("Corner Radius: ${cornerRadius.toInt()}dp")
            Slider(value = cornerRadius, onValueChange = { cornerRadius = it },
                valueRange = 0f..40f, modifier = Modifier.fillMaxWidth())

            SectionLabel("Widget Size (cells)")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Width: $cellW", fontSize = 13.sp)
                    Slider(value = cellW.toFloat(), onValueChange = { cellW = it.toInt() },
                        valueRange = 1f..5f, steps = 3)
                }
                Column(Modifier.weight(1f)) {
                    Text("Height: $cellH", fontSize = 13.sp)
                    Slider(value = cellH.toFloat(), onValueChange = { cellH = it.toInt() },
                        valueRange = 1f..5f, steps = 3)
                }
            }

            OnClickActionField(onClickAction) { onClickAction = it }

            Spacer(Modifier.height(32.dp))
        }
    }
}
