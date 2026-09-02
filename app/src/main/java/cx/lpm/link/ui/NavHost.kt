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
    const val ACTIVITY = "activity"
    const val GIT_REVIEW = "git/{name}"
    const val AUTOMATIONS = "automations"
    const val MEMORY = "memory/{name}"
    const val USAGE = "usage"
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
                    },
                    onNavigateToActivity = {
                        navController.navigate(Routes.ACTIVITY)
                    },
                    onNavigateToAutomations = {
                        navController.navigate(Routes.AUTOMATIONS)
                    }
                )
            }

            composable(Routes.PROJECT_DETAIL) {
                cx.lpm.link.ui.projects.ProjectDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onTerminalClick = { proj, termId ->
                        navController.navigate("terminal/$proj/$termId")
                    },
                    onNavigateToGit = { proj ->
                        navController.navigate("git/$proj")
                    },
                    onNavigateToMemory = { proj ->
                        navController.navigate("memory/$proj")
                    }
                )
            }

            composable(Routes.TERMINAL) {
                cx.lpm.link.ui.terminal.TerminalScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ACTIVITY) {
                cx.lpm.link.ui.activity.ActivityScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onTerminalClick = { proj, termId ->
                        navController.navigate("terminal/$proj/$termId")
                    },
                    onNavigateToUsage = {
                        navController.navigate(Routes.USAGE)
                    }
                )
            }

            composable(Routes.GIT_REVIEW) {
                cx.lpm.link.ui.git.GitReviewScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.AUTOMATIONS) {
                cx.lpm.link.ui.automations.AutomationsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.MEMORY) {
                cx.lpm.link.ui.memory.MemoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.USAGE) {
                cx.lpm.link.ui.usage.UsageScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
