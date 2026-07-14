package com.jonahzander.rsvpreader.presentation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.jonahzander.rsvpreader.data.ProgressStore
import com.jonahzander.rsvpreader.sync.DataLayerImport
import kotlinx.coroutines.launch

@Composable
fun WearApp(app: ReaderApplication, launchBookId: String? = null, launchNonce: Int = 0) {
    val navController = rememberSwipeDismissableNavController()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        app.bookRepository.refresh()
        DataLayerImport.importAll(app, app.bookRepository, app.progressStore)
    }

    // Tile tap (cold start or onNewIntent): jump straight into the reader; it
    // restores the saved position itself. A stale book id stays on the library.
    LaunchedEffect(launchBookId, launchNonce) {
        if (launchBookId != null && app.bookRepository.get(launchBookId) != null) {
            navController.navigate("reader/$launchBookId") {
                // One reader entry only: a tap while a reader is already open
                // would otherwise stack a second live session for the same
                // book, and the buried one saves stale positions on exit.
                popUpTo("library")
                launchSingleTop = true
            }
        }
    }
    val books by app.bookRepository.books.collectAsStateWithLifecycle()
    val settings by app.progressStore.settings.collectAsStateWithLifecycle(ReaderSettings())
    val wordSize by app.progressStore.wordSize.collectAsStateWithLifecycle(ProgressStore.DEFAULT_WORD_SIZE)
    val readingModeKey by app.progressStore.readingMode.collectAsStateWithLifecycle(ReadingMode.Rsvp.key)
    // Must stay a lazy state read: nav destination lambdas are cached by the
    // nav graph, so they only see new values through state they read themselves.
    // An eager `val readingMode = fromKey(...)` here freezes the value inside
    // the cached destination lambdas (settings chips never re-highlight).
    val readingMode by remember { derivedStateOf { ReadingMode.fromKey(readingModeKey) } }

    var swipeBackEnabled by remember { mutableStateOf(true) }
    val activity = LocalActivity.current

    // The reader's paused word line owns horizontal swipes, so it disables
    // swipe-back — but only while the reader is actually the top destination.
    // The nav host keeps the reader composed beneath pushed screens (Settings,
    // Chapters), so without this gate the disable would leak to them.
    val currentEntry by navController.currentBackStackEntryFlow
        .collectAsStateWithLifecycle(initialValue = null)
    val readerOnTop = currentEntry?.destination?.route?.startsWith("reader/") == true

    MaterialTheme {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "library",
            userSwipeEnabled = swipeBackEnabled || !readerOnTop,
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
                    wordSize = wordSize,
                    mode = readingMode,
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
                    wordSize = wordSize,
                    readingMode = readingMode,
                    onWpmChange = { scope.launch { app.progressStore.setWpm(it) } },
                    onFontChange = { scope.launch { app.progressStore.setFont(it) } },
                    onWordSizeChange = { scope.launch { app.progressStore.setWordSize(it) } },
                    onReadingModeChange = { scope.launch { app.progressStore.setReadingMode(it.key) } },
                )
            }
        }
    }
}
