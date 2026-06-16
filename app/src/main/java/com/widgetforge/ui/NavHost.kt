package com.widgetforge.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.widgetforge.ui.dashboard.DashboardScreen
import com.widgetforge.ui.editor.CodeEditorScreen
import com.widgetforge.ui.editor.TextEditorScreen
import com.widgetforge.ui.editor.ImagePickerScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val TEXT_EDITOR = "text_editor?widgetId={widgetId}"
    const val IMAGE_PICKER = "image_picker?widgetId={widgetId}&type={type}"
    const val CODE_EDITOR = "code_editor?widgetId={widgetId}"

    fun textEditor(widgetId: Int = -1) = "text_editor?widgetId=$widgetId"
    fun imagePicker(widgetId: Int = -1, type: String = "IMAGE") = "image_picker?widgetId=$widgetId&type=$type"
    fun codeEditor(widgetId: Int = -1) = "code_editor?widgetId=$widgetId"
}

@Composable
fun WidgetForgeNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onCreateText = { navController.navigate(Routes.textEditor()) },
                onCreateImage = { navController.navigate(Routes.imagePicker(type = "IMAGE")) },
                onCreateGif = { navController.navigate(Routes.imagePicker(type = "GIF")) },
                onCreateCode = { navController.navigate(Routes.codeEditor()) },
                onEditWidget = { entry ->
                    when (entry.widgetType.name) {
                        "TEXT" -> navController.navigate(Routes.textEditor(entry.appWidgetId))
                        "IMAGE" -> navController.navigate(Routes.imagePicker(entry.appWidgetId, "IMAGE"))
                        "GIF" -> navController.navigate(Routes.imagePicker(entry.appWidgetId, "GIF"))
                        "CODE" -> navController.navigate(Routes.codeEditor(entry.appWidgetId))
                    }
                }
            )
        }

        composable(
            "text_editor?widgetId={widgetId}",
            arguments = listOf(navArgument("widgetId") { type = NavType.IntType; defaultValue = -1 })
        ) { backStack ->
            TextEditorScreen(
                widgetId = backStack.arguments?.getInt("widgetId") ?: -1,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            "image_picker?widgetId={widgetId}&type={type}",
            arguments = listOf(
                navArgument("widgetId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("type") { type = NavType.StringType; defaultValue = "IMAGE" }
            )
        ) { backStack ->
            ImagePickerScreen(
                widgetId = backStack.arguments?.getInt("widgetId") ?: -1,
                type = backStack.arguments?.getString("type") ?: "IMAGE",
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            "code_editor?widgetId={widgetId}",
            arguments = listOf(navArgument("widgetId") { type = NavType.IntType; defaultValue = -1 })
        ) { backStack ->
            CodeEditorScreen(
                widgetId = backStack.arguments?.getInt("widgetId") ?: -1,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
