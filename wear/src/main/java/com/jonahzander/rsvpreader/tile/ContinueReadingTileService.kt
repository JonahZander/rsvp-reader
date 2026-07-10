package com.jonahzander.rsvpreader.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.jonahzander.rsvpreader.ReaderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val RESOURCES_VERSION = "1"

/** Home-screen tile: the last word read, tap to resume reading. */
class ContinueReadingTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            serviceScope.launch {
                try {
                    completer.set(buildTile())
                } catch (t: Throwable) {
                    completer.setException(t)
                }
            }
            "ContinueReadingTile"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "ContinueReadingTileResources"
        }

    private suspend fun buildTile(): TileBuilders.Tile {
        val app = application as ReaderApplication
        app.bookRepository.refresh()
        val info = loadContinueReadingInfo(app.progressStore) { app.bookRepository.get(it) }
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder().setRoot(root(info)).build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun root(info: ContinueReadingInfo?): LayoutElementBuilders.LayoutElement {
        val launchActivity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName("com.jonahzander.rsvpreader.presentation.MainActivity")
            .apply {
                if (info != null) {
                    addKeyToExtraMapping(
                        "com.jonahzander.rsvpreader.extra.BOOK_ID",
                        ActionBuilders.stringExtra(info.bookId),
                    )
                }
            }
            .build()
        val openApp = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder().setAndroidActivity(launchActivity).build()
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (info == null) {
            column.addContent(line("Pick a book", Typography.TYPOGRAPHY_TITLE3, 0xFFFFFFFF))
            column.addContent(line("to start reading", Typography.TYPOGRAPHY_BODY2, 0xB3FFFFFF))
        } else {
            column.addContent(line(info.lastWord, Typography.TYPOGRAPHY_TITLE1, 0xFFFFFFFF))
            column.addContent(line(info.bookTitle, Typography.TYPOGRAPHY_BODY2, 0xB3FFFFFF))
            column.addContent(
                line(
                    "Chapter ${info.chapterNumber}/${info.chapterCount}",
                    Typography.TYPOGRAPHY_CAPTION1,
                    0x80FFFFFF,
                )
            )
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp).build())
            .addContent(column.build())
            .build()
    }

    private fun line(text: String, typography: Int, color: Long) =
        Text.Builder(this, text)
            .setTypography(typography)
            .setColor(argb(color.toInt()))
            .setMaxLines(1)
            .build()
}
