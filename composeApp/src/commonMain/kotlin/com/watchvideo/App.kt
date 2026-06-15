package com.watchvideo

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watchvideo.ui.search.SearchScreen
import com.watchvideo.ui.settings.SettingsScreen
import com.watchvideo.ui.detail.DetailScreen
import io.ktor.http.encodeURLParameter
import io.ktor.http.decodeURLPart

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("search", "搜索", Icons.Default.Search),
    TabItem("settings", "设置", Icons.Default.Settings)
)

@Composable
fun App() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 底部导航仅在顶层 Tab 目的地显示，详情页全屏无底栏
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo("search") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "search",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("search") {
                SearchScreen(
                    onResultClick = { result ->
                        val encodedTitle = result.title.encodeURLParameter()
                        navController.navigate("detail/${result.siteKey}/${result.id}/$encodedTitle")
                    }
                )
            }
            composable("settings") {
                SettingsScreen()
            }
            composable(
                route = "detail/{siteKey}/{id}/{title}",
                arguments = listOf(
                    navArgument("siteKey") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { entry ->
                val siteKey = entry.arguments?.getString("siteKey") ?: return@composable
                val id = entry.arguments?.getString("id") ?: return@composable
                val title = (entry.arguments?.getString("title") ?: "").decodeURLPart()
                DetailScreen(
                    siteKey = siteKey,
                    vodId = id,
                    title = title,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
