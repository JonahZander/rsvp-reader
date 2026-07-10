package com.jonahzander.rsvpreader.presentation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.jonahzander.rsvpreader.ReaderApplication
import com.jonahzander.rsvpreader.core.sync.ReaderSettings
import com.jonahzander.rsvpreader.sync.DataLayerImport
import kotlinx.coroutines.launch

@Composable
fun WearApp(app: ReaderApplication) {
    val navController = rememberSwipeDismissableNavController()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        app.bookRepository.refresh()
        DataLayerImport.importAll(app, app.bookRepository, app.progressStore)
    }
    val books by app.bookRepository.books.collectAsStateWithLifecycle()
    val settings by app.progressStore.settings.collectAsStateWithLifecycle(ReaderSettings())

    var swipeBackEnabled by remember { mutableStateOf(true) }
    val activity = LocalContext.current as? Activity

    MaterialTheme {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "library",
            userSwipeEnabled = swipeBackEnabled,
        ) {
            composable("library") {
                LibraryScreen(
                    books = books,
                    onOpenBook = { navController.navigate("book/${it.id}") },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("book/{bookId}") { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                val book = books.firstOrNull { it.id == bookId } ?: return@composable
                ChapterListScreen(
                    book = book,
                    onContinueReading = { navController.navigate("reader/$bookId") },
                    onOpenChapter = { navController.navigate("reader/$bookId?chapter=$it") },
                )
            }
            composable(
                route = "reader/{bookId}?chapter={chapter}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("chapter") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                val chapter = entry.arguments?.getInt("chapter")?.takeIf { it >= 0 }
                val readerViewModel: ReaderViewModel = viewModel(
                    key = "reader-$bookId-$chapter",
                    factory = ReaderViewModel.factory(app, bookId, chapter),
                )
                ReaderScreen(
                    viewModel = readerViewModel,
                    font = settings.font,
                    onScrubbingChanged = { swipeBackEnabled = !it },
                    onOpenChapters = {
                        navController.navigate("book/$bookId") {
                            popUpTo("library")
                            launchSingleTop = true
                        }
                    },
                    onOpenLibrary = { navController.popBackStack("library", inclusive = false) },
                    onOpenSettings = { navController.navigate("settings") },
                    onExit = { activity?.finish() },
                )
            }
            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    onWpmChange = { scope.launch { app.progressStore.setWpm(it) } },
                    onFontChange = { scope.launch { app.progressStore.setFont(it) } },
                )
            }
        }
    }
}
