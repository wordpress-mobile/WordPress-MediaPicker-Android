package org.wordpress.android.mediapicker.insert

import android.content.Context
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.MediaStore
import org.wordpress.android.R
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.MediaActionBuilder
import org.wordpress.android.modules.IO_THREAD
import org.wordpress.android.mediapicker.MediaItem.Identifier
import org.wordpress.android.mediapicker.MediaItem.Identifier.GifMediaIdentifier
import org.wordpress.android.mediapicker.MediaItem.Identifier.LocalId
import org.wordpress.android.mediapicker.insert.MediaInsertHandler.InsertModel
import org.wordpress.android.util.FluxCUtils
import org.wordpress.android.util.WPMediaUtils
import javax.inject.Inject
import javax.inject.Named

class GifMediaInsertUseCase(
    private val context: Context,
    private val site: SiteModel,
    private val mediaStore: MediaStore,
    private val dispatcher: Dispatcher,
    private val ioDispatcher: CoroutineDispatcher
) : MediaInsertUseCase {
    override val actionTitle: Int
        get() = R.string.media_uploading_gif_library_photo

    override suspend fun insert(identifiers: List<Identifier>) = flow {
        emit(InsertModel.Progress(actionTitle))
        emit(coroutineScope {
                return@coroutineScope try {
                    val mediaIdentifiers = identifiers.mapNotNull { identifier ->
                        (identifier as? GifMediaIdentifier)?.let {
                            fetchAndSaveAsync(this, it, site)
                        }
                    }

                    InsertModel.Success(mediaIdentifiers.awaitAll())
                } catch (e: CancellationException) {
                    InsertModel.Success(listOf<GifMediaIdentifier>())
                } catch (e: Exception) {
                    InsertModel.Error(context.getString(R.string.error_downloading_image))
                }
            }
        )
    }

    private fun fetchAndSaveAsync(
        scope: CoroutineScope,
        gifIdentifier: GifMediaIdentifier,
        site: SiteModel
    ): Deferred<LocalId> = scope.async(ioDispatcher) {
        return@async gifIdentifier.largeImageUri.let { mediaUri ->
            // No need to log the Exception here. The underlying method that is used, [MediaUtils.downloadExternalMedia]
            // already logs any errors.
            val downloadedUri = WPMediaUtils.fetchMedia(context, mediaUri.uri)
                    ?: throw Exception("Failed to download the image.")

            // Exit if the parent coroutine has already been cancelled
            yield()

            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(mediaUri.toString())
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)

            val mediaModel = FluxCUtils.mediaModelFromLocalUri(context, downloadedUri, mimeType, mediaStore, site.id)
            mediaModel.title = gifIdentifier.title
            dispatcher.dispatch(MediaActionBuilder.newUpdateMediaAction(mediaModel))

            LocalId(mediaModel.id)
        }
    }

    class GifMediaInsertUseCaseFactory
    @Inject constructor(
        private val context: Context,
        private val mediaStore: MediaStore,
        private val dispatcher: Dispatcher,
        @Named(IO_THREAD) private val ioDispatcher: CoroutineDispatcher
    ) {
        fun build(site: SiteModel): GifMediaInsertUseCase {
            return GifMediaInsertUseCase(context, site, mediaStore, dispatcher, ioDispatcher)
        }
    }
}
