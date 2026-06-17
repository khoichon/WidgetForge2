package com.widgetforge.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.widgetforge.data.models.WidgetTemplate
import com.widgetforge.data.models.WidgetType
import com.widgetforge.ui.dashboard.DashboardScreen
import com.widgetforge.ui.editor.CodeEditorScreen
import com.widgetforge.ui.editor.ImagePickerScreen
import com.widgetforge.ui.editor.TextEditorScreen

object Routes {
    const val DASHBOARD  = "dashboard"

    fun textEditor (widgetId: Int = -1) = "text_editor?widgetId=$widgetId"
    fun imagePicker(widgetId: Int = -1, type: String = "IMAGE") = "image_picker?widgetId=$widgetId&type=$type"
    fun codeEditor (widgetId: Int = -1) = "code_editor?widgetId=$widgetId"
}

@Composable
fun WidgetForgeNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onCreateText  = { nav.navigate(Routes.textEditor())  },
                onCreateImage = { nav.navigate(Routes.imagePicker(type = "IMAGE")) },
                onCreateGif   = { nav.navigate(Routes.imagePicker(type = "GIF"))   },
                onCreateCode  = { nav.navigate(Routes.codeEditor())  },
                onEditWidget  = { template ->
                    when (template.widgetType) {
                        WidgetType.TEXT  -> nav.navigate(Routes.textEditor (template.id.toInt()))
                        WidgetType.IMAGE -> nav.navigate(Routes.imagePicker(template.id.toInt(), "IMAGE"))
                        WidgetType.GIF   -> nav.navigate(Routes.imagePicker(template.id.toInt(), "GIF"))
                        WidgetType.CODE  -> nav.navigate(Routes.codeEditor (template.id.toInt()))
                    }
                }
            )
        }

        composable(
            "text_editor?widgetId={widgetId}",
            arguments = listOf(navArgument("widgetId") { type = NavType.IntType; defaultValue = -1 })
        ) { back ->
            TextEditorScreen(
                widgetId = back.arguments?.getInt("widgetId") ?: -1,
                onBack   = { nav.popBackStack() }
            )
        }

        composable(
            "image_picker?widgetId={widgetId}&type={type}",
            arguments = listOf(
                navArgument("widgetId") { type = NavType.IntType;   defaultValue = -1 },
                navArgument("type")     { type = NavType.StringType; defaultValue = "IMAGE" }
            )
        ) { back ->
            ImagePickerScreen(
                widgetId = back.arguments?.getInt("widgetId") ?: -1,
                type     = back.arguments?.getString("type") ?: "IMAGE",
                onBack   = { nav.popBackStack() }
            )
        }

        composable(
            "code_editor?widgetId={widgetId}",
            arguments = listOf(navArgument("widgetId") { type = NavType.IntType; defaultValue = -1 })
        ) { back ->
            CodeEditorScreen(
                widgetId = back.arguments?.getInt("widgetId") ?: -1,
                onBack   = { nav.popBackStack() }
            )
        }
    }
}
