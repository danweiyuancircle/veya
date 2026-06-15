package com.watchvideo

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watchvideo.ui.search.SearchScreen
import com.watchvideo.ui.history.HistoryScreen
import com.watchvideo.ui.favorites.FavoritesScreen
import com.watchvideo.ui.settings.SettingsScreen
import com.watchvideo.ui.detail.DetailScreen
import com.watchvideo.ui.theme.VeyaTheme
import com.watchvideo.ui.update.UpdateDialog
import com.watchvideo.ui.image.buildImageLoader
import com.watchvideo.data.update.UpdateChecker
import com.watchvideo.data.update.UpdateInfo
import coil3.compose.setSingletonImageLoaderFactory
import io.ktor.http.encodeURLParameter
import io.ktor.http.decodeURLPart

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("search", "搜索", Icons.Default.Search),
    TabItem("history", "历史", Icons.Default.History),
    TabItem("favorites", "收藏", Icons.Default.Favorite),
    TabItem("settings", "设置", Icons.Default.Settings)
)

@Composable
fun App() = VeyaTheme {
    // 配置全局图片加载器：内存+磁盘缓存，避免封面实时加载失败/重复请求
    setSingletonImageLoaderFactory { context -> buildImageLoader(context) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 启动自动检查更新，有新版弹框；失败静默不打扰
    var startupUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        startupUpdate = UpdateChecker().checkLatest(currentAppVersion())
    }

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
            composable("history") {
                HistoryScreen(
                    onItemClick = { item ->
                        val siteKey = item.sourceKey
                        if (siteKey.isBlank()) return@HistoryScreen
                        val id = item.sourceContentId.ifBlank { item.contentKey }
                        navController.navigate("detail/$siteKey/$id/${item.title.encodeURLParameter()}")
                    }
                )
            }
            composable("favorites") {
                FavoritesScreen(
                    onItemClick = { item ->
                        val siteKey = item.preferredSourceKey?.ifBlank { null } ?: return@FavoritesScreen
                        val id = item.sourceContentId?.ifBlank { null } ?: item.contentKey
                        navController.navigate("detail/$siteKey/$id/${item.title.encodeURLParameter()}")
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

    startupUpdate?.let { info ->
        UpdateDialog(info = info, onDismiss = { startupUpdate = null })
    }
}
