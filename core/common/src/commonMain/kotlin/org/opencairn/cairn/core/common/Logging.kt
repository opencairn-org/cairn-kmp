package org.opencairn.cairn.core.common

import co.touchlab.kermit.Logger

/** One logger per subsystem; tags show up as-is on logcat and stdout. */
fun logger(tag: String): Logger = Logger.withTag(tag)
