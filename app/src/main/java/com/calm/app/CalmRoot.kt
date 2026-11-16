package com.calm.app

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.calm.core.browser.BrowserScreen
import com.calm.core.downloader.DownloadScreen
import com.calm.core.filemanager.FileManagerScreen
import com.calm.core.filemanager.ImageViewerScreen
import com.calm.core.filemanager.PdfViewerScreen
import com.calm.core.filemanager.TextViewerScreen
import com.calm.core.reader.BookshelfScreen
import com.calm.core.reader.ReaderScreen
import com.calm.core.settings.SettingsScreen

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("home", "首页", Icons.Filled.Home),
    TabItem("downloads", "下载", Icons.Filled.Download),
    TabItem("files", "文件", Icons.Filled.Folder),
    TabItem("bookshelf", "书架", Icons.Filled.MenuBook),
)

@Composable
fun CalmRoot(onThemeChanged: (Int, Int) -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Android 13+ 通知权限（下载进度通知）
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                BrowserScreen(onOpenSettings = { navController.navigate("settings") })
            }
            composable("downloads") { DownloadScreen() }
            composable("files") {
                FileManagerScreen(
                    onOpenImage = { path ->
                        navController.navigate("image/${Uri.encode(path)}")
                    },
                    onOpenText = { path ->
                        navController.navigate("text/${Uri.encode(path)}")
                    },
                    onOpenPdf = { path ->
                        navController.navigate("pdf/${Uri.encode(path)}")
                    },
                )
            }
            composable("bookshelf") {
                BookshelfScreen(onOpenBook = { id -> navController.navigate("reader/$id") })
            }

            composable("settings") {
                SettingsScreen(onThemeChanged = onThemeChanged)
            }
            composable("reader/{bookId}") { entry ->
                val bookId = entry.arguments?.getString("bookId")?.toLongOrNull() ?: return@composable
                ReaderScreen(
                    bookId = bookId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("image/{path}") { entry ->
                val path = entry.arguments?.getString("path")?.let { Uri.decode(it) } ?: return@composable
                ImageViewerScreen(path)
            }
            composable("text/{path}") { entry ->
                val path = entry.arguments?.getString("path")?.let { Uri.decode(it) } ?: return@composable
                TextViewerScreen(path)
            }
            composable("pdf/{path}") { entry ->
                val path = entry.arguments?.getString("path")?.let { Uri.decode(it) } ?: return@composable
                PdfViewerScreen(path)
            }
        }
    }
}
