package org.wordpress.android.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.wordpress.android.mediapicker.MediaPickerFragment;
import org.wordpress.android.mediapicker.R;
import org.wordpress.android.mediapicker.model.EditImageData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.wordpress.android.mediapicker.MediaPickerConstants.ARG_EDIT_IMAGE_DATA;

public class WPMediaUtils {

    private static final String TAG = "MediaPicker";

    public interface LaunchCameraCallback {
        void onMediaCapturePathReady(String mediaCapturePath);
    }

    private static void showSDCardRequiredDialog(Context context) {
        AlertDialog.Builder dialogBuilder = new MaterialAlertDialogBuilder(context);
        dialogBuilder.setTitle(context.getResources().getText(R.string.sdcard_title));
        dialogBuilder.setMessage(context.getResources().getText(R.string.sdcard_message));
        dialogBuilder.setPositiveButton(context.getString(android.R.string.ok), (dialog, whichButton) -> dialog.dismiss());
        dialogBuilder.setCancelable(true);
        dialogBuilder.create().show();
    }

    public static void launchChooserWithContext(
            Activity activity,
            MediaPickerFragment.MediaPickerAction.OpenSystemPicker openSystemPicker,
            int requestCode
    ) {
        activity.startActivityForResult(prepareChooserIntent(activity, openSystemPicker),
                requestCode);
    }

    private static Intent prepareChooserIntent(
            Context context,
            MediaPickerFragment.MediaPickerAction.OpenSystemPicker openSystemPicker
    ) {
        MediaPickerFragment.ChooserContext chooserContext = openSystemPicker.getChooserContext();
        Intent intent = new Intent(chooserContext.getIntentAction());
        intent.setType(chooserContext.getMediaTypeFilter());
        intent.putExtra(Intent.EXTRA_MIME_TYPES, openSystemPicker.getMimeTypes().toArray(new String[0]));
        if (openSystemPicker.getAllowMultipleSelection()) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return Intent.createChooser(intent, UiHelper.INSTANCE.getTextOfUiString(context, chooserContext.getTitle()));
    }

    public static void launchCamera(Activity activity, String applicationId, LaunchCameraCallback callback) {
        Intent intent = prepareLaunchCamera(activity, applicationId, callback);
        if (intent != null) {
            // wzieba TODO: 20/07/2021 Sync request code with library consumer
            activity.startActivityForResult(intent, 2100);
        }
    }

    private static Intent prepareLaunchCamera(Context context, String applicationId, LaunchCameraCallback callback) {
        String state = Environment.getExternalStorageState();
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            showSDCardRequiredDialog(context);
            return null;
        } else {
            try {
                return getLaunchCameraIntent(context, applicationId, callback);
            } catch (IOException e) {
                // No need to write log here
                return null;
            }
        }
    }

    private static Intent getLaunchCameraIntent(Context context, String applicationId, LaunchCameraCallback callback)
            throws IOException {
        File externalStoragePublicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        String mediaCapturePath =
                externalStoragePublicDirectory + File.separator + "Camera" + File.separator + "wp-" + System
                        .currentTimeMillis() + ".jpg";

        // make sure the directory we plan to store the recording in exists
        File directory = new File(mediaCapturePath).getParentFile();
        if (directory == null || (!directory.exists() && !directory.mkdirs())) {
            try {
                throw new IOException("Path to file could not be created: " + mediaCapturePath);
            } catch (IOException e) {
                Log.e(TAG, "", e);
                throw e;
            }
        }

        Uri fileUri;
        try {
            fileUri = FileProvider.getUriForFile(context, applicationId + ".provider", new File(mediaCapturePath));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Cannot access the file planned to store the new media", e);
            throw new IOException("Cannot access the file planned to store the new media");
        } catch (NullPointerException e) {
            Log.e(TAG, "Cannot access the file planned to store the new media - "
                    + "FileProvider.getUriForFile cannot find a valid provider for the authority: "
                    + applicationId + ".provider", e);
            throw new IOException("Cannot access the file planned to store the new media");
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

        if (callback != null) {
            callback.onMediaCapturePathReady(mediaCapturePath);
        }
        return intent;
    }

    public static int getPlaceholder(String url) {
        if (MediaUtils.isValidImage(url)) {
            return R.drawable.ic_image_white_24dp;
        } else if (MediaUtils.isDocument(url)) {
            return R.drawable.ic_pages_white_24dp;
        } else if (MediaUtils.isPowerpoint(url)) {
            return R.drawable.media_powerpoint;
        } else if (MediaUtils.isSpreadsheet(url)) {
            return R.drawable.media_spreadsheet;
        } else if (MediaUtils.isVideo(url)) {
            return R.drawable.ic_video_camera_white_24dp;
        } else if (MediaUtils.isAudio(url)) {
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
    public static void scanMediaFile(@NonNull Context context, @NonNull String localMediaPath) {
        MediaScannerConnection.scanFile(context,
                new String[]{localMediaPath}, null,
                (path, uri) -> Log.d(TAG, "Media scanner finished scanning " + path));
    }


    /**
     * Downloads the {@code mediaUri} and returns the {@link Uri} for the downloaded file
     * <p>
     * If the {@code mediaUri} is already in the the local store, no download will be done and the given
     * {@code mediaUri} will be returned instead. This may return null if the download fails.
     * <p>
     * The current thread is blocked until the download is finished.
     *
     * @return A local {@link Uri} or null if the download failed
     */
    public static @Nullable
    Uri fetchMedia(@NonNull Context context, @NonNull Uri mediaUri) {
        if (MediaUtils.isInMediaStore(mediaUri)) {
            return mediaUri;
        }

        try {
            // Do not download the file in async task. See
            // https://github.com/wordpress-mobile/WordPress-Android/issues/5818
            return MediaUtils.downloadExternalMedia(context, mediaUri);
        } catch (IllegalStateException e) {
            // Ref: https://github.com/wordpress-mobile/WordPress-Android/issues/5823
            AppLog.e(AppLog.T.UTILS, "Can't download the image at: " + mediaUri.toString()
                    + " See issue #5823", e);

            return null;
        }
    }

    public static List<Uri> retrieveImageEditorResult(Intent data) {
        if (data != null && data.hasExtra(ARG_EDIT_IMAGE_DATA)) {
            return convertEditImageOutputToListOfUris(
                    data.getParcelableArrayListExtra(ARG_EDIT_IMAGE_DATA)
            );
        } else {
            return new ArrayList<>();
        }
    }

    private static List<Uri> convertEditImageOutputToListOfUris(List<EditImageData.OutputData> data) {
        List<Uri> uris = new ArrayList<>(data.size());
        for (EditImageData.OutputData item : data) {
            uris.add(Uri.parse(item.getOutputFilePath()));
        }
        return uris;
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
