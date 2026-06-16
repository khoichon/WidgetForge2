package com.widgetforge.ui.editor

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.widgetforge.ui.theme.WidgetForgeTheme
import com.widgetforge.ui.WidgetForgeNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * WidgetConfigActivity — launched by the Android AppWidget system when
 * the user drags a widget onto the homescreen.
 *
 * Receives the appWidgetId from the launcher, hosts the full editor flow,
 * and finishes with RESULT_OK so Android commits the widget placement.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract appWidgetId from the launcher intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If invalid, cancel immediately
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        // Set CANCELED as default so back-press without saving cancels widget placement
        setResult(RESULT_CANCELED, resultIntent())
        enableEdgeToEdge()

        setContent {
            WidgetForgeTheme {
                WidgetConfigHost(
                    appWidgetId = appWidgetId,
                    onSaved = {
                        setResult(RESULT_OK, resultIntent())
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED, resultIntent())
                        finish()
                    }
                )
            }
        }
    }

    private fun resultIntent() = Intent().apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigHost(
    appWidgetId: Int,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedType by remember { mutableStateOf<String?>(null) }

    if (selectedType == null) {
        // Type picker
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Choose Widget Type", fontWeight = FontWeight.Bold) }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Select the type of widget to place on your homescreen",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
                Spacer(Modifier.height(16.dp))

                listOf(
                    Triple("TEXT", "Text Widget", "Display custom rich text with styling"),
                    Triple("IMAGE", "Image Widget", "Show a high-resolution static image"),
                    Triple("GIF", "Animated GIF", "Loop an animated GIF on your homescreen"),
                    Triple("CODE", "Code Widget", "Run JavaScript canvas drawing logic")
                ).forEach { (type, title, desc) ->
                    OutlinedCard(
                        onClick = { selectedType = type },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(title, fontWeight = FontWeight.SemiBold)
                            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    } else {
        // Delegate to the appropriate editor screen with the real appWidgetId
        // The editor ViewModels call registry.register() which triggers onSaved
        when (selectedType) {
            "TEXT" -> TextEditorScreen(widgetId = appWidgetId, onBack = { selectedType = null })
            "IMAGE" -> ImagePickerScreen(widgetId = appWidgetId, type = "IMAGE", onBack = { selectedType = null })
            "GIF" -> ImagePickerScreen(widgetId = appWidgetId, type = "GIF", onBack = { selectedType = null })
            "CODE" -> CodeEditorScreen(widgetId = appWidgetId, onBack = { selectedType = null })
        }
    }
}
