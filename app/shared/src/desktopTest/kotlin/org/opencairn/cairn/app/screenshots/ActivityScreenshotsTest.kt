package org.opencairn.cairn.app.screenshots

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.opencairn.cairn.feature.activities.ActivitiesScreen
import org.opencairn.cairn.feature.activities.ActivitiesViewModel
import org.opencairn.cairn.feature.activities.ActivityDetailActions
import org.opencairn.cairn.feature.activities.ActivityDetailScreen
import org.opencairn.cairn.feature.activities.ActivityDetailViewModel
import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ActivityScreenshotsTest {
    @BeforeTest
    fun setUp() {
        // View models run on Main; resources and dates follow the JVM defaults.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.ENGLISH)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun activityList() =
        Device.entries.forEach { device ->
            screenshot("activities", device) {
                ActivitiesScreen(ActivitiesViewModel(SampleActivities()), formatters, dates, onOpen = {})
            }
        }

    @Test
    fun activityDetail() =
        Device.entries.forEach { device ->
            // Tall enough for the whole page: every section below the summary is part of the picture.
            screenshot("activity_detail", device, height = 1900) {
                ActivityDetailScreen(
                    ActivityDetailViewModel(SampleActivities(), SampleInsights(), "a0"),
                    formatters,
                    dates,
                    prefs.units,
                    ActivityDetailActions(onBack = {}, onOpenMap = {}, onOpenStreams = {}, onOpenActivity = {}),
                )
            }
        }
}
