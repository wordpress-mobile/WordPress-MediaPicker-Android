package org.wordpress.android.mediapicker.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

abstract class ScopedViewModel(
    protected val savedState: SavedStateHandle
) : ViewModel(), CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = viewModelScope.coroutineContext
}
