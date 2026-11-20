package org.opencairn.cairn.app.screenshots

import org.opencairn.cairn.feature.account.AccountActions
import org.opencairn.cairn.feature.account.AccountScreen
import org.opencairn.cairn.feature.account.AccountViewModel
import org.opencairn.cairn.feature.account.ConnectionDetailScreen
import org.opencairn.cairn.feature.account.ConnectionsScreen
import org.opencairn.cairn.feature.account.ConnectionsViewModel
import org.opencairn.cairn.feature.account.NotificationsScreen
import org.opencairn.cairn.feature.account.NotificationsViewModel
import org.opencairn.cairn.feature.overview.OverviewActions
import org.opencairn.cairn.feature.overview.OverviewScreen
import org.opencairn.cairn.feature.overview.OverviewViewModel
import kotlin.test.Test

class AccountScreenshotsTest : ScreenshotTest() {
    @Test
    fun overviewWithSyncNotice() =
        onEveryDevice("overview", height = 1100) {
            OverviewScreen(
                OverviewViewModel(SampleOverview(), SampleSync()),
                formatters,
                dates,
                federationEnabled = false,
                OverviewActions(onOpenActivity = {}, onOpenConnections = {}),
            )
        }

    @Test
    fun accountPage() =
        onEveryDevice("account") {
            AccountScreen(
                AccountViewModel(SampleAthlete(), SampleGear(), SampleRecords()),
                sampleUser,
                dates,
                unread = 2,
                AccountActions({}, {}, {}, {}, {}, {}, {}, {}),
            )
        }

    @Test
    fun connections() =
        onEveryDevice("connections") {
            ConnectionsScreen(ConnectionsViewModel(SampleSync()), dates, onBack = {}, onOpen = {})
        }

    @Test
    fun connectionDetail() =
        onEveryDevice("connection_detail") {
            val vm = ConnectionsViewModel(SampleSync(), detailOf = "x1")
            ConnectionDetailScreen(vm, "x1", dates, onBack = {}, onOpenActivity = {})
        }

    @Test
    fun notifications() =
        onEveryDevice("notifications") {
            NotificationsScreen(NotificationsViewModel(SampleNotifications()), dates, onBack = {}, onOpenActivity = {})
        }
}
