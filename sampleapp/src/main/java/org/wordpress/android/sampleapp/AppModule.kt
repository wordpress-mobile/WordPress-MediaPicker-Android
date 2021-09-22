package org.wordpress.android.sampleapp

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.wordpress.android.mediapicker.api.MimeTypeSupportProvider

@Module
@InstallIn(SingletonComponent::class)
class AppModule {
    @Provides
    fun provideBackgroundDispatcher(): CoroutineDispatcher {
        return Dispatchers.Default
    }

    @Provides
    fun provideMimeTypeProvider(): MimeTypeSupportProvider {
        return MimeTypeProvider()
    }
}
