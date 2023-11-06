package org.wordpress.android.mediapicker.ui

import android.content.Intent
import android.net.Uri
import org.wordpress.android.mediapicker.MediaPickerConstants
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.GifMedia
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.LocalMedia
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.RemoteMedia
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.model.asMediaUri

internal object ResultIntentHelper {
    private fun Intent.putUris(
        mediaUris: List<MediaUri>
    ) {
        this.putExtra(
            MediaPickerConstants.EXTRA_MEDIA_URIS,
            mediaUris.map(MediaUri::asAndroidUri).toStringArray()
        )
    }

    private fun Intent.putQueuedUris(
        mediaUris: List<MediaUri>
    ) {
        this.putExtra(
            MediaPickerConstants.EXTRA_MEDIA_QUEUED_URIS,
            mediaUris.map(MediaUri::asAndroidUri).toStringArray()
        )
    }

    private fun Intent.putRemoteMedia(
        remoteMedia: List<RemoteMedia>
    ) {
        this.putParcelableArrayListExtra(
            MediaPickerConstants.EXTRA_REMOTE_MEDIA,
            ArrayList(remoteMedia)
        )
    }

    private fun Intent.putLocalIds(
        mediaLocalIds: List<Int>
    ) {
        this.putExtra(
            MediaPickerConstants.EXTRA_SAVED_MEDIA_MODEL_LOCAL_IDS,
            mediaLocalIds.toIntArray()
        )
    }

    fun getCapturedImageResultIntent(areResultsQueued: Boolean, uri: Uri): Intent {
        val intent = Intent()
        val capturedImageUri = listOf(uri.asMediaUri())
        if (areResultsQueued) {
            intent.putQueuedUris(capturedImageUri)
        } else {
            intent.putUris(capturedImageUri)
        }
        intent.putExtra(
            MediaPickerConstants.EXTRA_MEDIA_SOURCE,
            DataSource.CAMERA.name
        )
        return intent
    }

    fun getSelectedMediaResultIntent(
        identifiers: List<Identifier>,
        dataSource: DataSource
    ): Intent {
        val chosenLocalUris = identifiers.mapNotNull { (it as? LocalUri) }
        val chosenGifUris = identifiers.mapNotNull { (it as? GifMedia) }
        val chosenUris = chosenLocalUris.filter { !it.queued }.map { it.uri }
        val queuedUris = chosenLocalUris.filter { it.queued }.map { it.uri }
        val chosenRemoteMedia = identifiers.mapNotNull { (it as? RemoteMedia) }
        val chosenLocalIds = identifiers.mapNotNull { (it as? LocalMedia)?.id }

        val intent = Intent()
        if (chosenUris.isNotEmpty()) {
            intent.putUris(chosenUris)
        } else if (chosenGifUris.isNotEmpty()) {
            intent.putUris(chosenGifUris.map { it.uri })
        }
        if (queuedUris.isNotEmpty()) {
            intent.putQueuedUris(queuedUris)
        }
        if (chosenRemoteMedia.isNotEmpty()) {
            intent.putRemoteMedia(chosenRemoteMedia)
        }
        if (chosenLocalIds.isNotEmpty()) {
            intent.putLocalIds(chosenLocalIds)
        }
        intent.putExtra(
            MediaPickerConstants.EXTRA_MEDIA_SOURCE,
            dataSource.name
        )
        return intent
    }

    private fun List<Uri>.toStringArray() = this.map { it.toString() }.toTypedArray()
}
