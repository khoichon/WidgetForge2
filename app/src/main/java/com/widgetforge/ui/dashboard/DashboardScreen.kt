package com.widgetforge.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.widgetforge.data.models.WidgetTemplate
import com.widgetforge.data.models.WidgetType
import com.widgetforge.ui.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onCreateText: () -> Unit,
    onCreateImage: () -> Unit,
    onCreateGif: () -> Unit,
    onCreateCode: () -> Unit,
    onEditWidget: (WidgetTemplate) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsState()
    var showFab      by remember { mutableStateOf(false) }
    var toDelete     by remember { mutableStateOf<WidgetTemplate?>(null) }
    var filterType   by remember { mutableStateOf<WidgetType?>(null) }
    val snackbar     = remember { SnackbarHostState() }

    LaunchedEffect(uiState.exportPath) {
        uiState.exportPath?.let { snackbar.showSnackbar("Exported to: $it"); viewModel.clearExportPath() }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    val filtered = remember(uiState.templates, filterType) {
        if (filterType == null) uiState.templates
        else uiState.templates.filter { it.widgetType == filterType }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WidgetForge", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(
                            "${uiState.templates.size} template${if (uiState.templates.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(visible = showFab) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FabOption("Code Widget",  Icons.Default.Code,       Color(0xFF6C9EFF)) { showFab = false; onCreateCode()  }
                        FabOption("Animated GIF", Icons.Default.Gif,        Color(0xFFFF6B9D)) { showFab = false; onCreateGif()   }
                        FabOption("Static Image", Icons.Default.Image,      Color(0xFF4ADE80)) { showFab = false; onCreateImage() }
                        FabOption("Text Widget",  Icons.Default.TextFields, Color(0xFFFBBF24)) { showFab = false; onCreateText()  }
                    }
                }
                FloatingActionButton(
                    onClick = { showFab = !showFab },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(if (showFab) Icons.Default.Close else Icons.Default.Add, "Create")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── How-to banner (shown when there are templates but no placed widgets) ──
            if (uiState.templates.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.5f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.AddToHomeScreen, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "Long-press your home screen → Widgets → WidgetForge to place a template",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // ── Filter chips ──────────────────────────────────────────────────
            if (uiState.templates.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(selected = filterType == null, onClick = { filterType = null },
                            label = { Text("All (${uiState.templates.size})") })
                    }
                    items(WidgetType.values().toList()) { type ->
                        val count = uiState.templates.count { it.widgetType == type }
                        if (count > 0) {
                            FilterChip(
                                selected = filterType == type,
                                onClick  = { filterType = if (filterType == type) null else type },
                                label    = { Text("${typeName(type)} ($count)") },
                                leadingIcon = {
                                    Icon(typeIcon(type), null, Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }

            // ── Template list / empty state ───────────────────────────────────
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                EmptyState(hasTemplates = uiState.templates.isNotEmpty())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            onEdit   = { onEditWidget(template) },
                            onExport = { viewModel.exportTemplate(template) },
                            onDelete = { toDelete = template }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    toDelete?.let { tpl ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title   = { Text("Delete Template") },
            text    = { Text("Remove \"${tpl.label}\"? Any placed widgets using this template will stop updating.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTemplate(tpl); toDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ─── Template Card ────────────────────────────────────────────────────────────

@Composable
private fun TemplateCard(
    template: WidgetTemplate,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val (color, icon) = typeColorIcon(template.widgetType)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(template.label,
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(typeName(template.widgetType),
                            fontSize = 12.sp, color = color)
                    }
                }
                Row {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.FileDownload, "Export",
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit",
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(0.7f))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.25f))
            Spacer(Modifier.height(10.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip("Size", "${template.cellWidth}×${template.cellHeight}")
                StatChip("ID", "#${template.id}")
                if (template.onClickAction.isNotBlank()) {
                    StatChip("Action", "Tap →", tint = color)
                } else {
                    StatChip("Action", "None")
                }
            }
        }
    }
}

// ─── Small helpers ────────────────────────────────────────────────────────────

@Composable
private fun StatChip(label: String, value: String, tint: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint)
    }
}

@Composable
private fun FabOption(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Surface(shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 4.dp) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        SmallFloatingActionButton(onClick = onClick, containerColor = color, contentColor = Color.White) {
            Icon(icon, label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyState(hasTemplates: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Widgets, null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(0.25f))
            Spacer(Modifier.height(16.dp))
            Text(if (hasTemplates) "No templates match filter" else "No templates yet",
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Spacer(Modifier.height(8.dp))
            Text(if (hasTemplates) "Try clearing the type filter"
                 else "Tap  +  to create your first widget template",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.35f))
        }
    }
}

private fun typeName(type: WidgetType) = when (type) {
    WidgetType.TEXT  -> "Text Widget"
    WidgetType.IMAGE -> "Static Image"
    WidgetType.GIF   -> "Animated GIF"
    WidgetType.CODE  -> "Code Widget"
}

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
