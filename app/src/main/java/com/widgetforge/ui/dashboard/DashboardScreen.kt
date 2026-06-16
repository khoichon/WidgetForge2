package com.widgetforge.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.widgetforge.data.models.WidgetRegistryEntry
import com.widgetforge.data.models.WidgetType
import com.widgetforge.ui.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onCreateText: () -> Unit,
    onCreateImage: () -> Unit,
    onCreateGif: () -> Unit,
    onCreateCode: () -> Unit,
    onEditWidget: (WidgetRegistryEntry) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateMenu by remember { mutableStateOf(false) }
    var widgetToDelete by remember { mutableStateOf<WidgetRegistryEntry?>(null) }

    // Snackbar for export
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.exportPath) {
        uiState.exportPath?.let {
            snackbarHostState.showSnackbar("Exported to: $it")
            viewModel.clearExportPath()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WidgetForge", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(
                            "${uiState.widgets.size} widget${if (uiState.widgets.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = showCreateMenu) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CreateWidgetOption(
                            icon = Icons.Default.Code,
                            label = "Code Widget",
                            color = Color(0xFF6C9EFF),
                            onClick = { showCreateMenu = false; onCreateCode() }
                        )
                        CreateWidgetOption(
                            icon = Icons.Default.Gif,
                            label = "Animated GIF",
                            color = Color(0xFFFF6B9D),
                            onClick = { showCreateMenu = false; onCreateGif() }
                        )
                        CreateWidgetOption(
                            icon = Icons.Default.Image,
                            label = "Static Image",
                            color = Color(0xFF4ADE80),
                            onClick = { showCreateMenu = false; onCreateImage() }
                        )
                        CreateWidgetOption(
                            icon = Icons.Default.TextFields,
                            label = "Text Widget",
                            color = Color(0xFFFBBF24),
                            onClick = { showCreateMenu = false; onCreateText() }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
                FloatingActionButton(
                    onClick = { showCreateMenu = !showCreateMenu },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (showCreateMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Create widget"
                    )
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.widgets.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.widgets, key = { it.appWidgetId }) { widget ->
                    WidgetCard(
                        entry = widget,
                        onEdit = { onEditWidget(widget) },
                        onExport = { viewModel.exportWidget(widget) },
                        onDelete = { widgetToDelete = widget }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Delete confirmation dialog
    widgetToDelete?.let { widget ->
        AlertDialog(
            onDismissRequest = { widgetToDelete = null },
            title = { Text("Delete Widget") },
            text = { Text("Remove \"${widget.label}\" from your collection? The homescreen widget will also be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWidget(widget)
                    widgetToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { widgetToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun WidgetCard(
    entry: WidgetRegistryEntry,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val (typeColor, typeIcon, typeLabel) = widgetTypeInfo(entry.widgetType)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(typeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeIcon, contentDescription = null, tint = typeColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            entry.label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            typeLabel,
                            fontSize = 12.sp,
                            color = typeColor
                        )
                    }
                }
                Row {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export",
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(0.7f))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoChip(label = "Size", value = "${entry.cellWidth}×${entry.cellHeight}")
                InfoChip(label = "ID", value = "#${entry.appWidgetId}")
                InfoChip(
                    label = "Res",
                    value = if (entry.pixelWidth > 0) "${entry.pixelWidth}×${entry.pixelHeight}px" else "Auto"
                )
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CreateWidgetOption(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp
        ) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = color,
            contentColor = Color.White
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Widgets,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No widgets yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap + to create your first widget",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
        }
    }
}

private fun widgetTypeInfo(type: WidgetType): Triple<Color, ImageVector, String> = when (type) {
    WidgetType.TEXT -> Triple(Color(0xFFFBBF24), Icons.Default.TextFields, "Text Widget")
    WidgetType.IMAGE -> Triple(Color(0xFF4ADE80), Icons.Default.Image, "Static Image")
    WidgetType.GIF -> Triple(Color(0xFFFF6B9D), Icons.Default.Gif, "Animated GIF")
    WidgetType.CODE -> Triple(Color(0xFF6C9EFF), Icons.Default.Code, "Code Widget")
}
