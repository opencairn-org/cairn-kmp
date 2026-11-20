package org.opencairn.cairn.app.screenshots

import org.opencairn.cairn.feature.activities.ActivitiesScreen
import org.opencairn.cairn.feature.activities.ActivitiesViewModel
import org.opencairn.cairn.feature.activities.ActivityDetailActions
import org.opencairn.cairn.feature.activities.ActivityDetailScreen
import org.opencairn.cairn.feature.activities.ActivityDetailViewModel
import kotlin.test.Test

class ActivityScreenshotsTest : ScreenshotTest() {
    @Test
    fun activityList() =
        onEveryDevice("activities") {
            ActivitiesScreen(ActivitiesViewModel(SampleActivities()), formatters, dates, onOpen = {})
        }

    // Tall enough for the whole page: every section below the summary is part of the picture.
    @Test
    fun activityDetail() =
        onEveryDevice("activity_detail", height = 1900) {
            ActivityDetailScreen(
                ActivityDetailViewModel(SampleActivities(), SampleInsights(), "a0"),
                formatters,
                dates,
                prefs.units,
                ActivityDetailActions(onBack = {}, onOpenMap = {}, onOpenStreams = {}, onOpenActivity = {}),
            )
        }
}
