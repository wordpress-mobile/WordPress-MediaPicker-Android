package org.wordpress.android.mediapicker

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.STOCK_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.WP_LIBRARY
import org.wordpress.android.util.LocaleManagerWrapper

@RunWith(MockitoJUnitRunner::class)
class MediaLoaderFactoryTest {
    @Mock lateinit var deviceListBuilder: DeviceListBuilder
    @Mock lateinit var localeManagerWrapper: LocaleManagerWrapper
    private lateinit var mediaLoaderFactory: MediaLoaderFactory
    private val mediaPickerSetup = MediaPickerSetup(DEVICE, true, setOf(), false)

    @Before
    fun setUp() {
        mediaLoaderFactory = MediaLoaderFactory(deviceListBuilder, localeManagerWrapper)
    }

    @Test
    fun `returns device list builder on DEVICE source`() {
        val mediaLoader = mediaLoaderFactory.build(mediaPickerSetup)

        assertThat(mediaLoader).isEqualTo(
                MediaLoader(
                        deviceListBuilder,
                        localeManagerWrapper,
                        mediaPickerSetup.allowedTypes
                )
        )
    }

    @Test
    fun `throws exception on not implemented sources`() {
        assertThatExceptionOfType(NotImplementedError::class.java).isThrownBy {
            mediaLoaderFactory.build(
                    mediaPickerSetup.copy(dataSource = GIF_LIBRARY)
            )
        }
        assertThatExceptionOfType(NotImplementedError::class.java).isThrownBy {
            mediaLoaderFactory.build(mediaPickerSetup.copy(dataSource = STOCK_LIBRARY))
        }
        assertThatExceptionOfType(NotImplementedError::class.java).isThrownBy {
            mediaLoaderFactory.build(
                    mediaPickerSetup.copy(dataSource = WP_LIBRARY)
            )
        }
    }
}
