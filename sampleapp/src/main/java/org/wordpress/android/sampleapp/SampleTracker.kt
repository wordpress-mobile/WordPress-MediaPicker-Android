package org.wordpress.android.sampleapp

import org.wordpress.android.mediapicker.util.Tracker
import org.wordpress.android.mediapicker.util.Tracker.Stat
import javax.inject.Inject

class SampleTracker @Inject constructor() : Tracker {
    override fun track(stat: Stat, properties: Map<String, Any?>) {
        println("Tracker ${stat.name}: $properties")
    }
}