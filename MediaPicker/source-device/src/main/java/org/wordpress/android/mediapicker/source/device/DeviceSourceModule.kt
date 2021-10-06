package org.wordpress.android.mediapicker.source.device

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.mediapicker.api.MimeTypeSupportProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DeviceSourceModule {
    @Singleton
    @Provides
    fun provideDeviceMediaSourceFactory(
        deviceMediaLoader: DeviceMediaLoader,
        mimeTypeSupportProvider: MimeTypeSupportProvider,
        coroutineDispatcher: CoroutineDispatcher
    ): DeviceMediaSource.Factory {
        return DeviceMediaSource.Factory(
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
}