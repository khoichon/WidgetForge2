package com.widgetforge.ui.editor

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.widgetforge.data.models.*
import com.widgetforge.data.repository.WidgetRegistry
import com.widgetforge.ui.theme.WidgetForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: WidgetRegistry
) : ViewModel() {

    val templates: StateFlow<List<WidgetTemplate>> = registry
        .observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Place a template as a real homescreen widget instance */
    fun placeTemplate(template: WidgetTemplate, appWidgetId: Int) {
        viewModelScope.launch {
            registry.register(
                WidgetRegistryEntry(
                    appWidgetId   = appWidgetId,
                    widgetType    = template.widgetType,
                    sourceFilePath = template.sourceFilePath,
                    label         = template.label,
                    cellWidth     = template.cellWidth,
                    cellHeight    = template.cellHeight,
                    onClickAction = template.onClickAction
                )
            )
        }
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setResult(RESULT_CANCELED, resultIntent())
        enableEdgeToEdge()

        setContent {
            WidgetForgeTheme {
                WidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    onPlaced = {
                        setResult(RESULT_OK, resultIntent())
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED, resultIntent())
                        finish()
                    },
                    onCreateNew = { type ->
                        // Launch app to create a new template, then come back
                        val intent = Intent(this, com.widgetforge.ui.MainActivity::class.java).apply {
                            putExtra("create_type", type)
                            putExtra("from_widget_config", true)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    private fun resultIntent() = Intent().apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
}

// ─── Config Screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    appWidgetId: Int,
    onPlaced: () -> Unit,
    onCancel: () -> Unit,
    onCreateNew: (String) -> Unit,
    viewModel: WidgetConfigViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsState()
    var filterType by remember { mutableStateOf<WidgetType?>(null) }
    var selectedTemplate by remember { mutableStateOf<WidgetTemplate?>(null) }

    val filtered = remember(templates, filterType) {
        if (filterType == null) templates else templates.filter { it.widgetType == filterType }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Place Widget", fontWeight = FontWeight.Bold)
                        Text(
                            "Choose a template or create new",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        },
        bottomBar = {
            selectedTemplate?.let { tpl ->
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedTemplate = null },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear") }
                        Button(
                            onClick = {
                                viewModel.placeTemplate(tpl, appWidgetId)
                                onPlaced()
                            },
                            modifier = Modifier.weight(2f)
                        ) {
                            Icon(Icons.Default.AddToHomeScreen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Place on Home Screen")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Type filter chips ────────────────────────────────────────────
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterType == null,
                        onClick = { filterType = null },
                        label = { Text("All") }
                    )
                }
                items(WidgetType.values().toList()) { type ->
                    FilterChip(
                        selected = filterType == type,
                        onClick = { filterType = if (filterType == type) null else type },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = {
                            Icon(
                                imageVector = typeIcon(type),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // ── Create new strip ─────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                    Text(
                        "Create new template:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    listOf("TEXT" to Icons.Default.TextFields,
                           "IMAGE" to Icons.Default.Image,
                           "GIF" to Icons.Default.Gif,
                           "CODE" to Icons.Default.Code).forEach { (type, icon) ->
                        IconButton(
                            onClick = { onCreateNew(type) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(icon, contentDescription = type,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── Template list ────────────────────────────────────────────────
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Widgets, null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.25f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No templates yet",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        )
                        Text(
                            "Create one using the buttons above",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            isSelected = selectedTemplate?.id == template.id,
                            onClick = {
                                selectedTemplate =
                                    if (selectedTemplate?.id == template.id) null else template
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ─── Template Card ────────────────────────────────────────────────────────────

@Composable
private fun TemplateCard(
    template: WidgetTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (color, icon) = typeColorIcon(template.widgetType)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isSelected) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Type icon badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    template.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Grid size badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = color.copy(0.12f)
                    ) {
                        Text(
                            "${template.cellWidth}×${template.cellHeight}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // onClick indicator
                    if (template.onClickAction.isNotBlank()) {
                        Icon(
                            Icons.Default.TouchApp, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        template.widgetType.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }
            }

            // Selected indicator
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun typeIcon(type: WidgetType): ImageVector = when (type) {
    WidgetType.TEXT  -> Icons.Default.TextFields
    WidgetType.IMAGE -> Icons.Default.Image
    WidgetType.GIF   -> Icons.Default.Gif
    WidgetType.CODE  -> Icons.Default.Code
}

private fun typeColorIcon(type: WidgetType): Pair<Color, ImageVector> = when (type) {
    WidgetType.TEXT  -> Color(0xFFFBBF24) to Icons.Default.TextFields
    WidgetType.IMAGE -> Color(0xFF4ADE80) to Icons.Default.Image
    WidgetType.GIF   -> Color(0xFFFF6B9D) to Icons.Default.Gif
    WidgetType.CODE  -> Color(0xFF6C9EFF) to Icons.Default.Code
}
