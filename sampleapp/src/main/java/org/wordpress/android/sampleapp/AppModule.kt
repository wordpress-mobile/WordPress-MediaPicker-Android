package org.wordpress.android.sampleapp

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.wordpress.android.mediapicker.api.MediaSourceFactory
import org.wordpress.android.mediapicker.api.MimeTypeSupportProvider
import org.wordpress.android.mediapicker.source.device.DeviceMediaSource.DeviceMediaSourceFactory
import org.wordpress.android.mediapicker.source.device.DeviceMediaLoader
import org.wordpress.android.mediapicker.util.Log
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    companion object {
        @Singleton
        @Provides
        fun provideBackgroundDispatcher(): CoroutineDispatcher {
            return Dispatchers.Default
        }

        @Singleton
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

        @Singleton
        @Provides
        fun provideDeviceMediaLoader(
            @ApplicationContext context: Context,
            mimeTypeSupportProvider: MimeTypeSupportProvider
        ): DeviceMediaLoader {
            return DeviceMediaLoader(context, mimeTypeSupportProvider)
        }

        @Singleton
        @Provides
        fun providesCoroutineScope(dispatcher: CoroutineDispatcher): CoroutineScope {
            return CoroutineScope(SupervisorJob() + dispatcher)
        }
    }

    @Binds
    abstract fun bindMimeTypeProvider(mimeTypeProvider: MimeTypeProvider): MimeTypeSupportProvider

    @Binds
    abstract fun bindLogger(mimeTypeProvider: Logger): Log
}