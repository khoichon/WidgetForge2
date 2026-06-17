package com.widgetforge.ui

import android.content.Context
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
    val templates: List<WidgetTemplate> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val exportPath: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: WidgetRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            registry.observeTemplates().collect { templates ->
                _uiState.update { it.copy(templates = templates, isLoading = false) }
            }
        }
    }

    fun exportTemplate(template: WidgetTemplate) {
        viewModelScope.launch {
            val exportDir = File(context.getExternalFilesDir(null), "exports").also { it.mkdirs() }
            val name = template.label.replace(" ", "_").replace(Regex("[^A-Za-z0-9_-]"), "")
            try {
                val outFile = when (template.widgetType) {
                    WidgetType.TEXT -> {
                        val config = WidgetExportEngine.importText(File(template.sourceFilePath))
                        WidgetExportEngine.exportText(config, exportDir, name)
                    }
                    WidgetType.IMAGE -> {
                        val meta = WidgetExportEngine.readImageMeta(File(template.sourceFilePath))
                        WidgetExportEngine.exportImage(
                            ImageWidgetConfig(
                                imagePath     = template.sourceFilePath,
                                label         = meta?.label ?: template.label,
                                onClickAction = meta?.onClickAction ?: template.onClickAction,
                                cellWidth     = meta?.cellWidth ?: template.cellWidth,
                                cellHeight    = meta?.cellHeight ?: template.cellHeight,
                                cornerRadius  = meta?.cornerRadius ?: 12f
                            ), exportDir, name
                        )
                    }
                    WidgetType.GIF -> {
                        val meta = WidgetExportEngine.readGifMeta(File(template.sourceFilePath))
                        WidgetExportEngine.exportGif(
                            GifWidgetConfig(
                                gifPath       = template.sourceFilePath,
                                label         = meta?.label ?: template.label,
                                onClickAction = meta?.onClickAction ?: template.onClickAction,
                                cellWidth     = meta?.cellWidth ?: template.cellWidth,
                                cellHeight    = meta?.cellHeight ?: template.cellHeight,
                                cornerRadius  = meta?.cornerRadius ?: 12f
                            ), exportDir, name
                        )
                    }
                    WidgetType.CODE -> File(template.sourceFilePath) // ZIP already correct
                }
                _uiState.update { it.copy(exportPath = outFile.absolutePath) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Export failed: ${e.message}") }
            }
        }
    }

    fun deleteTemplate(template: WidgetTemplate) {
        viewModelScope.launch {
            registry.deleteTemplate(template.id)
        }
    }

    fun clearError()      = _uiState.update { it.copy(error = null) }
    fun clearExportPath() = _uiState.update { it.copy(exportPath = null) }
}
