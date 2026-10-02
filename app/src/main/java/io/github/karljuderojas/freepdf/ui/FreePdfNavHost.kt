package io.github.karljuderojas.freepdf.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.karljuderojas.freepdf.ui.home.HomeScreen
import io.github.karljuderojas.freepdf.ui.viewer.ViewerScreen

private const val HOME = "home"
private const val VIEWER = "viewer?uri={uri}"

@Composable
fun FreePdfNavHost(incomingPdf: Uri?, onIncomingPdfHandled: () -> Unit) {
    val navController = rememberNavController()
    val openPdf: (Uri) -> Unit = { uri ->
        navController.navigate("viewer?uri=${Uri.encode(uri.toString())}")
    }

    LaunchedEffect(incomingPdf) {
        if (incomingPdf != null) {
            openPdf(incomingPdf)
            onIncomingPdfHandled()
        }
    }

    NavHost(navController = navController, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(onOpenPdf = openPdf)
        }
        composable(
            route = VIEWER,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            val uri = Uri.parse(entry.arguments?.getString("uri").orEmpty())
            ViewerScreen(uri = uri, onBack = { navController.popBackStack() })
        }
    }
}
