package org.wordpress.android.sampleapp

import org.wordpress.android.mediapicker.api.Tracker
import org.wordpress.android.mediapicker.api.Tracker.Event
import javax.inject.Inject

class SampleTracker @Inject constructor() : Tracker {
    override fun track(event: Event, properties: Map<String, Any?>) {
        println("Tracker ${event.name}: $properties")
    }
}
