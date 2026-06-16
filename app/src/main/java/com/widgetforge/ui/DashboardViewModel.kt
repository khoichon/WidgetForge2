package com.widgetforge.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.widgetforge.data.models.*
import com.widgetforge.data.repository.WidgetRegistry
import com.widgetforge.export.WidgetExportEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DashboardUiState(
    val widgets: List<WidgetRegistryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val exportPath: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: WidgetRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            registry.observeAll().collect { widgets ->
                _uiState.update { it.copy(widgets = widgets, isLoading = false) }
            }
        }
    }

    // ── Export Actions ──────────────────────────────────────────────────────

    fun exportWidget(entry: WidgetRegistryEntry) {
        viewModelScope.launch {
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            val name = entry.label.replace(" ", "_")
            try {
                val out = when (entry.widgetType) {
                    WidgetType.TEXT -> WidgetExportEngine.exportText(
                        WidgetExportEngine.importText(File(entry.sourceFilePath)),
                        exportDir, name
                    )
                    WidgetType.IMAGE -> WidgetExportEngine.exportImage(
                        entry.sourceFilePath, exportDir, name
                    )
                    WidgetType.GIF -> WidgetExportEngine.exportGif(
                        entry.sourceFilePath, exportDir, name
                    )
                    WidgetType.CODE -> File(entry.sourceFilePath) // ZIP already exists
                }
                _uiState.update { it.copy(exportPath = out.absolutePath) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Export failed: ${e.message}") }
            }
        }
    }

    fun deleteWidget(entry: WidgetRegistryEntry) {
        viewModelScope.launch {
            registry.unregister(entry.appWidgetId)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearExportPath() = _uiState.update { it.copy(exportPath = null) }
}
