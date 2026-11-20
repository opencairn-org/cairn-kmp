package org.opencairn.cairn.app.screenshots

import org.opencairn.cairn.feature.analysis.AnalysisScreen
import org.opencairn.cairn.feature.analysis.AnalysisViewModel
import org.opencairn.cairn.feature.analysis.StatsScreen
import org.opencairn.cairn.feature.analysis.StatsViewModel
import kotlin.test.Test

class AnalysisScreenshotsTest : ScreenshotTest() {
    @Test
    fun trainingLoad() =
        onEveryDevice("analysis") {
            val vm = AnalysisViewModel(SampleAnalytics()).apply { select(131) }
            AnalysisScreen(vm, dates, onOpenStats = {}, onOpenHeatmap = {})
        }

    @Test
    fun statistics() =
        onEveryDevice("stats") {
            StatsScreen(StatsViewModel(SampleAnalytics()), formatters, dates, onBack = {}, onOpenActivity = {})
        }
}
