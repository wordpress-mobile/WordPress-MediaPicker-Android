package org.wordpress.android.mediapicker.util;

import static org.wordpress.android.mediapicker.MediaPickerRequestCodes.TAKE_PHOTO;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.wordpress.android.mediapicker.ui.MediaPickerFragment;
import org.wordpress.android.mediapicker.R;
import org.wordpress.android.mediapicker.model.EditImageData;
import org.wordpress.android.mediapicker.model.MediaUri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MediaUtils {
    public static int getPlaceholder(String url) {
        if (org.wordpress.android.util.MediaUtils.isValidImage(url)) {
            return R.drawable.ic_image_white_24dp;
        } else if (org.wordpress.android.util.MediaUtils.isDocument(url)) {
            return R.drawable.ic_pages_white_24dp;
        } else if (org.wordpress.android.util.MediaUtils.isPowerpoint(url)) {
            return R.drawable.media_powerpoint;
        } else if (org.wordpress.android.util.MediaUtils.isSpreadsheet(url)) {
            return R.drawable.media_spreadsheet;
        } else if (org.wordpress.android.util.MediaUtils.isVideo(url)) {
            return R.drawable.ic_video_camera_white_24dp;
        } else if (org.wordpress.android.util.MediaUtils.isAudio(url)) {
            return R.drawable.ic_audio_white_24dp;
        } else {
            return R.drawable.ic_image_multiple_white_24dp;
        }
    }

    /*
     * passes a newly-created media file to the media scanner service so it's available to
     * the media content provider - use this after capturing or downloading media to ensure
     * that it appears in the stock Gallery app
     */
    public static void scanMediaFile(Log log, @NonNull Context context, @NonNull String localMediaPath) {
        MediaScannerConnection.scanFile(context,
                new String[]{localMediaPath}, null,
                (path, uri) -> log.d("Media scanner finished scanning " + path));
    }

    public static List<Uri> retrieveMediaUris(Intent data) {
        ClipData clipData = data.getClipData();
        ArrayList<Uri> uriList = new ArrayList<>();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                ClipData.Item item = clipData.getItemAt(i);
                uriList.add(item.getUri());
            }
        } else {
            uriList.add(data.getData());
        }
        return uriList;
    }

}
