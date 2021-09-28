package org.wordpress.android.sampleapp

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.wordpress.android.mediapicker.api.MediaSourceFactory
import org.wordpress.android.mediapicker.api.MimeTypeSupportProvider
import org.wordpress.android.mediapicker.source.devicemedia.DeviceMediaSource.DeviceMediaSourceFactory
import org.wordpress.android.mediapicker.source.devicemedia.DeviceMediaLoader

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    companion object {
        @Provides
        fun provideBackgroundDispatcher(): CoroutineDispatcher {
            return Dispatchers.Default
        }

        @Provides
        fun provideMediaSourceFactory(
            deviceMediaLoader: DeviceMediaLoader,
            mimeTypeSupportProvider: MimeTypeSupportProvider,
            coroutineDispatcher: CoroutineDispatcher
        ): MediaSourceFactory {
            return DeviceMediaSourceFactory(
                deviceMediaLoader,
                coroutineDispatcher,
                mimeTypeSupportProvider
            )
        }

        @Provides
        fun provideDeviceMediaLoader(
            @ApplicationContext context: Context,
            mimeTypeSupportProvider: MimeTypeSupportProvider
        ): DeviceMediaLoader {
            return DeviceMediaLoader(context, mimeTypeSupportProvider)
        }
    }

    @Binds
    abstract fun bindMimeTypeProvider(mimeTypeProvider: MimeTypeProvider): MimeTypeSupportProvider
}
