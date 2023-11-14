package org.wordpress.android.mediapicker.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

/**
 * Merges four LiveData sources using a given function. The function returns an object of a new type.
 * @param sourceA first source
 * @param sourceB second source
 * @param sourceC third source
 * @param sourceD fourth source
 * @return new data source
 */
fun <S, T, U, V, W> merge(
    sourceA: LiveData<S>,
    sourceB: LiveData<T>,
    sourceC: LiveData<U>,
    sourceD: LiveData<V>,
    distinct: Boolean = false,
    merger: (S?, T?, U?, V?) -> W?
): LiveData<W> {
    data class FourItemContainer(
        val first: S? = null,
        val second: T? = null,
        val third: U? = null,
        val fourth: V? = null
    )

    val mediator = MediatorLiveData<FourItemContainer>()
    mediator.value = FourItemContainer()
    mediator.addSource(sourceA) {
        val container = mediator.value
        if (container?.first != it || !distinct) {
            mediator.value = container?.copy(first = it)
        }
    }
    mediator.addSource(sourceB) {
        val container = mediator.value
        if (container?.second != it || !distinct) {
            mediator.value = container?.copy(second = it)
        }
    }
    mediator.addSource(sourceC) {
        val container = mediator.value
        if (container?.third != it || !distinct) {
            mediator.value = container?.copy(third = it)
        }
    }
    mediator.addSource(sourceD) {
        val container = mediator.value
        if (container?.fourth != it || !distinct) {
            mediator.value = container?.copy(fourth = it)
        }
    }
    return mediator.map { (first, second, third, fourth) -> merger(first, second, third, fourth) }
}

/**
 * Simple wrapper of the map utility method that is null safe
 */
fun <T, U> LiveData<T>.map(mapper: (T) -> U?): MediatorLiveData<U> {
    val result = MediatorLiveData<U>()
    result.addSource(this) { x -> result.value = x?.let { mapper(x) } }
    return result
}

/**
 * This method ensures that the LiveData instance doesn't emit the same item twice
 */
fun <T> LiveData<T>.distinct(): MediatorLiveData<T> {
    val mediatorLiveData: MediatorLiveData<T> = MediatorLiveData()
    mediatorLiveData.addSource(this) {
        if (it != mediatorLiveData.value) {
            mediatorLiveData.value = it
        }
    }
    return mediatorLiveData
}
