package org.wordpress.android.mediapicker.ui

import android.content.Intent
import android.net.Uri
import org.wordpress.android.mediapicker.MediaPickerConstants
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.asAndroidUri
import org.wordpress.android.mediapicker.util.asMediaUri

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

    private fun Intent.putMediaIds(
        mediaIds: List<Long>
    ) {
        this.putExtra(MediaPickerConstants.RESULT_IDS, mediaIds.toLongArray())
        this.putExtra(MediaPickerConstants.EXTRA_MEDIA_ID, mediaIds[0])
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
        val chosenLocalUris = identifiers.mapNotNull { (it as? Identifier.LocalUri) }
        val chosenGifUris = identifiers.mapNotNull { (it as? Identifier.GifMediaId) }
        val chosenUris = chosenLocalUris.filter { !it.queued }.map { it.uri }
        val queuedUris = chosenLocalUris.filter { it.queued }.map { it.uri }
        val chosenIds = identifiers.mapNotNull { (it as? Identifier.RemoteId)?.value }
        val chosenLocalIds = identifiers.mapNotNull { (it as? Identifier.LocalId)?.value }

        val intent = Intent()
        if (!chosenUris.isNullOrEmpty()) {
            intent.putUris(chosenUris)
        } else if (!chosenGifUris.isNullOrEmpty()) {
            intent.putUris(chosenGifUris.map { it.uri })
        }
        if (!queuedUris.isNullOrEmpty()) {
            intent.putQueuedUris(queuedUris)
        }
        if (!chosenIds.isNullOrEmpty()) {
            intent.putMediaIds(chosenIds)
        }
        if (!chosenLocalIds.isNullOrEmpty()) {
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
