package org.wordpress.android.sampleapp

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.wordpress.android.mediapicker.api.Log
import org.wordpress.android.mediapicker.api.MediaPickerSetupProvider
import org.wordpress.android.mediapicker.api.MimeTypeProvider
import org.wordpress.android.mediapicker.api.Tracker
import org.wordpress.android.mediapicker.loader.MediaLoaderFactory
import org.wordpress.android.mediapicker.source.gif.TenorApiKey
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
        fun providesCoroutineScope(dispatcher: CoroutineDispatcher): CoroutineScope {
            return CoroutineScope(SupervisorJob() + dispatcher)
        }

        @TenorApiKey
        @Singleton
        @Provides
        fun providesTenorKey(): String = BuildConfig.TENOR_API_KEY
    }

    @Binds
    abstract fun bindMimeTypeProvider(sampleMimeTypeProvider: SampleMimeTypeProvider): MimeTypeProvider

    @Binds
    abstract fun bindMediaLoaderFactory(
        sampleMediaLoaderFactory: SampleMediaLoaderFactory
    ): MediaLoaderFactory

    @Binds
    abstract fun bindLogger(mimeTypeProvider: Logger): Log

    @Binds
    abstract fun bindTracker(tracker: SampleTracker): Tracker

    @Binds
    abstract fun bindMediaPickerSetupProvider(
        provider: SampleMediaPickerSetupProvider
    ): MediaPickerSetupProvider
}
