package io.github.karljuderojas.freepdf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.karljuderojas.freepdf.FreePdfApp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.share.Sharing
import io.github.karljuderojas.freepdf.ui.files.FilesScreen
import io.github.karljuderojas.freepdf.ui.home.HomeContent
import io.github.karljuderojas.freepdf.ui.home.QuickAction
import io.github.karljuderojas.freepdf.ui.settings.SOURCE_URL
import io.github.karljuderojas.freepdf.ui.settings.SettingsContent
import io.github.karljuderojas.freepdf.ui.create.ImagesToPdfScreen
import io.github.karljuderojas.freepdf.ui.tools.CreateTool
import io.github.karljuderojas.freepdf.ui.tools.ToolsContent
import io.github.karljuderojas.freepdf.ui.viewer.ViewerMode
import io.github.karljuderojas.freepdf.ui.viewer.ViewerScreen

private const val MAIN = "main"
private const val VIEWER = "viewer?uri={uri}&mode={mode}&tool={tool}"
private const val IMAGES_TO_PDF = "images-to-pdf"
private const val NO_TOOL = 0

@Composable
fun FreePdfNavHost(incomingPdf: Uri?, onIncomingPdfHandled: () -> Unit) {
    val navController = rememberNavController()
    val openPdf: (Uri, ViewerMode, Int?) -> Unit = { uri, mode, tool ->
        navController.navigate(viewerRoute(uri, mode, tool))
    }

    LaunchedEffect(incomingPdf) {
        if (incomingPdf != null) {
            openPdf(incomingPdf, ViewerMode.Read, null)
            onIncomingPdfHandled()
        }
    }

    NavHost(navController = navController, startDestination = MAIN) {
        composable(MAIN) {
            MainScreen(openPdf, onCreateTool = { tool ->
                when (tool) {
                    CreateTool.ImagesToPdf -> navController.navigate(IMAGES_TO_PDF)
                }
            })
        }
        composable(IMAGES_TO_PDF) {
            ImagesToPdfScreen(
                onBack = { navController.popBackStack() },
                // The new PDF takes this screen's place, so Back from it returns to the tabs.
                onCreated = { uri ->
                    navController.navigate(viewerRoute(uri, ViewerMode.Read, null)) {
                        popUpTo(IMAGES_TO_PDF) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = VIEWER,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType; defaultValue = ViewerMode.Read.name },
                navArgument("tool") { type = NavType.IntType; defaultValue = NO_TOOL },
            ),
        ) { entry ->
            val args = entry.arguments
            val uri = Uri.parse(args?.getString("uri").orEmpty())
            val mode = ViewerMode.entries.firstOrNull { it.name == args?.getString("mode") } ?: ViewerMode.Read
            val tool = args?.getInt("tool")?.takeIf { it != NO_TOOL }
            ViewerScreen(
                uri = uri,
                onBack = { navController.popBackStack() },
                initialMode = mode,
                initialTool = tool,
                // The other document takes this one's place, so Back still returns to the tabs.
                onSwitchTo = { other ->
                    navController.navigate(viewerRoute(other, ViewerMode.Read, null)) {
                        popUpTo(VIEWER) { inclusive = true }
                    }
                },
            )
        }
    }
}

private fun viewerRoute(uri: Uri, mode: ViewerMode, tool: Int?): String =
    "viewer?uri=${Uri.encode(uri.toString())}&mode=${mode.name}&tool=${tool ?: NO_TOOL}"

/** The four tabs. Home's shortcuts and the Tools tab ask for a PDF, then open it in a mode. */
@Composable
private fun MainScreen(openPdf: (Uri, ViewerMode, Int?) -> Unit, onCreateTool: (CreateTool) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as FreePdfApp
    var tab by rememberSaveable { mutableStateOf(MainTab.Home) }

    // What to open the next picked PDF in; saved in case Android recreates the app while picking.
    var pendingMode by rememberSaveable { mutableStateOf(ViewerMode.Read) }
    var pendingTool by rememberSaveable { mutableIntStateOf(NO_TOOL) }
    val pick = rememberPdfPicker { uri -> openPdf(uri, pendingMode, pendingTool.takeIf { it != NO_TOOL }) }
    fun pickFor(mode: ViewerMode, tool: Int?) {
        pendingMode = mode
        pendingTool = tool ?: NO_TOOL
        pick()
    }

    val openEntry: (DocumentEntry) -> Unit = { openPdf(Uri.parse(it.uri), ViewerMode.Read, null) }
    val share: (DocumentEntry) -> Unit = { entry ->
        // The file may be gone or the grant revoked since it was last opened.
        runCatching { Sharing.shareUri(context, Uri.parse(entry.uri), entry.name) }
            .onFailure { Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show() }
    }

    AppShell(selected = tab, onSelect = { tab = it }) { selected, modifier ->
        when (selected) {
            MainTab.Home -> {
                val recent by app.documents.recent.collectAsStateWithLifecycle()
                HomeContent(
                    recent = recent,
                    onOpenFile = { pickFor(ViewerMode.Read, null) },
                    onQuickAction = { action ->
                        if (action.mode == null) tab = MainTab.Tools else pickFor(action.mode, action.tool)
                    },
                    onOpen = openEntry,
                    onShare = share,
                    onForget = { app.documents.forget(it.uri) },
                    onSeeAll = { tab = MainTab.Files },
                    modifier = modifier,
                )
            }
            MainTab.Files -> FilesScreen(onOpenPdf = { openPdf(it, ViewerMode.Read, null) }, modifier = modifier)
            MainTab.Tools -> ToolsContent(
                onToolPicked = { tool -> pickFor(tool.mode, tool.label.takeIf { tool.preselects }) },
                onCreateToolPicked = onCreateTool,
                modifier = modifier,
            )
            MainTab.Settings -> {
                val theme by app.settings.theme.collectAsStateWithLifecycle()
                val rememberHistory by app.settings.rememberHistory.collectAsStateWithLifecycle()
                val pageColors by app.settings.pageColors.collectAsStateWithLifecycle()
                val showTips by app.tips.enabled.collectAsStateWithLifecycle()
                SettingsContent(
                    theme = theme,
                    onTheme = app.settings::setTheme,
                    rememberHistory = rememberHistory,
                    onRememberHistory = app.settings::setRememberHistory,
                    onClearHistory = app.documents::clearHistory,
                    version = remember(context) { appVersion(context) },
                    onSourceCode = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) }
                    },
                    showTips = showTips,
                    onShowTips = app.tips::setEnabled,
                    onResetTips = {
                        app.tips.resetAll()
                        Toast.makeText(context, R.string.settings_reset_tips_done, Toast.LENGTH_SHORT).show()
                    },
                    modifier = modifier,
                    pageColors = pageColors,
                    onPageColors = app.settings::setPageColors,
                )
            }
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
