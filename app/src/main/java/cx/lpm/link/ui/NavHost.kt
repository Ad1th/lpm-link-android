package cx.lpm.link.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cx.lpm.link.ui.pairing.PairingScreen
import cx.lpm.link.ui.projects.ProjectsScreen

/**
 * Top-level navigation routes.
 */
object Routes {
    const val PAIRING = "pairing"
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "project/{name}"
    const val TERMINAL = "terminal/{projectName}/{terminalId}"
}

@Composable
fun LpmNavHost() {
    val navController = rememberNavController()

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.PAIRING,
        ) {
            composable(Routes.PAIRING) {
                PairingScreen(
                    onPaired = {
                        navController.navigate(Routes.PROJECTS) {
                            popUpTo(Routes.PAIRING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.PROJECTS) {
                ProjectsScreen(
                    onProjectClick = { name ->
                        navController.navigate("project/$name")
                    },
                    onNavigateToPairing = {
                        navController.navigate(Routes.PAIRING)
                    }
                )
            }

            composable(Routes.PROJECT_DETAIL) { backStack ->
                val name = backStack.arguments?.getString("name") ?: return@composable
                // TODO: ProjectDetailScreen
            }

            composable(Routes.TERMINAL) { backStack ->
                val projectName = backStack.arguments?.getString("projectName") ?: return@composable
                val terminalId = backStack.arguments?.getString("terminalId") ?: return@composable
                // TODO: TerminalScreen
            }
        }
    }
}
