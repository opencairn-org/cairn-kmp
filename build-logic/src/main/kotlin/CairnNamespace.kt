import org.gradle.api.Project

/** `org.opencairn.cairn.<path segments>`, e.g. `:core:network` → `org.opencairn.cairn.core.network`. */
val Project.cairnNamespace: String
    get() = "org.opencairn.cairn" + path.replace(':', '.').replace('-', '_')
