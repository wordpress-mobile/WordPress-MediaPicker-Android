package org.wordpress.android.mediapicker.loader

import android.content.Context
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.tenor.android.core.model.impl.Media
import com.tenor.android.core.model.impl.MediaCollection
import com.tenor.android.core.response.impl.GifsResponse
import kotlinx.coroutines.InternalCoroutinesApi
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.invocation.InvocationOnMock
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.test
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult
import com.tenor.android.core.model.impl.Result
import org.wordpress.android.R
import org.wordpress.android.mediapicker.loader.GifMediaDataSourceTest.GifResponseTestScenario.EmptyList
import org.wordpress.android.mediapicker.loader.GifMediaDataSourceTest.GifResponseTestScenario.PopulatedList
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.UriUtilsWrapper
import org.wordpress.android.util.UriWrapper

@InternalCoroutinesApi
class GifMediaDataSourceTest : BaseUnitTest() {
    @Mock lateinit var context: Context
    @Mock internal lateinit var tenorClient: TenorGifClient
    @Mock lateinit var gifsResponse: GifsResponse
    @Mock lateinit var responseResult: Result
    @Mock lateinit var uriUtilsWrapper: UriUtilsWrapper
    @Captor lateinit var onSuccessCalback: ArgumentCaptor<(GifsResponse) -> Unit>
    @Captor lateinit var onFailCalback: ArgumentCaptor<(Throwable?) -> Unit>

    private lateinit var gifMediaDataSource: GifMediaDataSource

    @Before
    fun setUp() {
        gifMediaDataSource = GifMediaDataSource(context, tenorClient, uriUtilsWrapper)
    }

    @Test
    fun `returns empty result when filter is empty`() = test {
        val filter = ""

        val result = gifMediaDataSource.load(forced = false, loadMore = false, filter = filter)

        (result as MediaLoadingResult.Empty).apply {
            Assertions.assertThat(
                    (this.title as UiStringRes).stringRes
            ).isEqualTo(R.string.gif_picker_initial_empty_text)
        }
    }

    @Test
    fun `returns success with items when matching filter`() = test {
        val filter = "cats"

        doAnswer { invocation: InvocationOnMock ->
            val callback: (GifsResponse) -> Unit = invocation.getArgument(3)
            callback.invoke(buildGifResponse(PopulatedList))
            null
        }.`when`(tenorClient).search(any(), any(), any(), any(), any())

        val result = gifMediaDataSource.load(forced = false, loadMore = false, filter = filter)

        (result as MediaLoadingResult.Success).apply {
            Assertions.assertThat(this.data).hasSize(2)
            Assertions.assertThat(this.hasMore).isTrue()
        }
    }

    @Test
    fun `returns empty result when not matching filter`() = test {
        val filter = "not matching filter"

        doAnswer { invocation: InvocationOnMock ->
            val callback: (GifsResponse) -> Unit = invocation.getArgument(3)
            callback.invoke(buildGifResponse(EmptyList))
            null
        }.`when`(tenorClient).search(any(), any(), any(), any(), any())

        val result = gifMediaDataSource.load(forced = false, loadMore = false, filter = filter)

        (result as MediaLoadingResult.Empty).apply {
            Assertions.assertThat(
                    (this.title as UiStringRes).stringRes
            ).isEqualTo(R.string.gif_picker_empty_search_list)
        }
    }

    @Test
    fun `returns failure on error`() = test {
        val filter = "exception while filtering"
        val errorMessage = "There was an error."

        doAnswer { invocation: InvocationOnMock ->
            val callback: (Throwable?) -> Unit = invocation.getArgument(4)
            callback.invoke(Throwable(errorMessage))
            null
        }.`when`(tenorClient).search(any(), any(), any(), any(), any())

        val result = gifMediaDataSource.load(forced = false, loadMore = false, filter = filter)

        (result as MediaLoadingResult.Failure).apply {
            Assertions.assertThat(this.message).isNotEmpty()
            Assertions.assertThat(this.message).isEqualTo(errorMessage)
        }
    }

    @Test
    fun `when paginating more items are returned`() = test {
        val filter = "cats"

        doAnswer { invocation: InvocationOnMock ->
            val callback: (GifsResponse) -> Unit = invocation.getArgument(3)
            callback.invoke(buildGifResponse(PopulatedList))
            null
        }.`when`(tenorClient).search(any(), any(), any(), any(), any())

        var result = gifMediaDataSource.load(forced = false, loadMore = true, filter = filter)

        (result as MediaLoadingResult.Success).apply {
            Assertions.assertThat(this.data).hasSize(2)
            Assertions.assertThat(this.hasMore).isTrue()
        }

        result = gifMediaDataSource.load(forced = false, loadMore = true, filter = filter)

        (result as MediaLoadingResult.Success).apply {
            Assertions.assertThat(this.data).hasSize(4)
            Assertions.assertThat(this.hasMore).isFalse()
        }
    }

    @Test
    fun `when loadMore is false items are cleared`() = test {
        val filter = "cats"

        doAnswer { invocation: InvocationOnMock ->
            val callback: (GifsResponse) -> Unit = invocation.getArgument(3)
            callback.invoke(buildGifResponse(PopulatedList))
            null
        }.`when`(tenorClient).search(any(), any(), any(), any(), any())

        var result = gifMediaDataSource.load(forced = false, loadMore = true, filter = filter)

        (result as MediaLoadingResult.Success).apply {
            Assertions.assertThat(this.data).hasSize(2)
            Assertions.assertThat(this.hasMore).isTrue()
        }

        result = gifMediaDataSource.load(forced = false, loadMore = false, filter = filter)

        (result as MediaLoadingResult.Success).apply {
            Assertions.assertThat(this.data).hasSize(2)
            Assertions.assertThat(this.hasMore).isTrue()
        }
    }

    private enum class GifResponseTestScenario {
        EmptyList,
        PopulatedList
    }

    private fun buildGifResponse(scenario: GifResponseTestScenario): GifsResponse {
        when (scenario) {
            EmptyList -> {
                whenever(gifsResponse.next).thenReturn("")
                whenever(gifsResponse.results).thenReturn(listOf())
            }
            PopulatedList -> {
                whenever(gifsResponse.next).thenReturn("2")

                val responseResult: Result = mock()
                val mediaCollection: MediaCollection = mock()
                val media: Media = mock()
                val uriWrapper: UriWrapper = mock()

                whenever(uriUtilsWrapper.parse(anyString())).thenReturn(uriWrapper)
                whenever(media.url).thenReturn("https://sampleurl.org")
                whenever(mediaCollection.get(anyString())).thenReturn(media)
                val tmpList = listOf(mediaCollection)
                whenever(responseResult.medias).thenReturn(tmpList)

                whenever(gifsResponse.results).thenReturn(listOf(responseResult, responseResult))
            }
        }

        return gifsResponse
    }
}
