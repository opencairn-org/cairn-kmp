package org.opencairn.cairn.feature.account

/** The update check's switch in the settings; absent on builds that update through a store or packager. */
data class UpdateSetting(
    val enabled: Boolean,
    val onChange: (Boolean) -> Unit,
)
