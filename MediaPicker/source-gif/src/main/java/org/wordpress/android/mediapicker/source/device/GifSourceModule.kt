package org.wordpress.android.mediapicker.source.device

import android.content.Context
import com.tenor.android.core.network.ApiClient
import com.tenor.android.core.network.ApiService
import com.tenor.android.core.network.ApiService.IBuilder
import com.tenor.android.core.network.IApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class GifSourceModule {
    @Provides
    fun provideTenorGifClient(@ApplicationContext context: Context): TenorGifClient {
        val builder: IBuilder<IApiClient> = ApiService.Builder(context, IApiClient::class.java)
        builder.apiKey("0402JHA0MUI6")
        ApiClient.init(context, builder)
        return TenorGifClient(context, ApiClient.getInstance(context))
    }
}