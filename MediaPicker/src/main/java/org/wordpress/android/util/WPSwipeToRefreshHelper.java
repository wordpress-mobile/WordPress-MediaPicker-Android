package org.wordpress.android.util;

import android.annotation.SuppressLint;
import android.content.Context;

import com.google.android.material.elevation.ElevationOverlayProvider;

import org.wordpress.android.util.helpers.SwipeToRefreshHelper;
import org.wordpress.android.util.helpers.SwipeToRefreshHelper.RefreshListener;
import org.wordpress.android.util.widgets.CustomSwipeRefreshLayout;
import org.wordpress.android.mediapicker.R;

public class WPSwipeToRefreshHelper {
    /**
     * Builds a {@link SwipeToRefreshHelper} and returns a new
     * instance with colors designated for the WordPress app.
     *
     * @param swipeRefreshLayout {@link CustomSwipeRefreshLayout} for refreshing the contents
     *                           of a view via a vertical swipe gesture.
     * @param listener           {@link RefreshListener} notified when a refresh is triggered
     *                           via the swipe gesture.
     */
    @SuppressLint("ResourceType")
    public static SwipeToRefreshHelper buildSwipeToRefreshHelper(CustomSwipeRefreshLayout swipeRefreshLayout,
                                                                 RefreshListener listener) {
        Context context = swipeRefreshLayout.getContext();

        ElevationOverlayProvider elevationOverlayProvider = new ElevationOverlayProvider(context);
        int appbarElevation = swipeRefreshLayout.getResources().getDimensionPixelOffset(R.dimen.minor_50);
        int backgroundColor = elevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(appbarElevation);

        int primaryProgressColor = ContextExtensionsKt.getColorResIdFromAttribute(context, R.attr.colorPrimary);
        int secondaryProgressColor = ContextExtensionsKt.getColorResIdFromAttribute(context, R.attr.colorSecondary);

        return new SwipeToRefreshHelper(swipeRefreshLayout, listener, backgroundColor, primaryProgressColor,
                secondaryProgressColor);
    }
}
