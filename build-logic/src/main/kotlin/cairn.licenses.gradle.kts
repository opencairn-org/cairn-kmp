// Third-party license report + allow-list gate for an application module.
// The module sets `licenseReport.configurations` to its runtime classpath(s).
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.render.TextReportRenderer

plugins {
    id("com.github.jk1.dependency-license-report")
}

licenseReport {
    outputDir =
        layout.buildDirectory
            .dir("reports/licenses")
            .get()
            .asFile.path
    renderers = arrayOf(TextReportRenderer("THIRD-PARTY-NOTICES.txt"), JsonReportRenderer("licenses.json"))
    filters = arrayOf(LicenseBundleNormalizer())
    allowedLicensesFile = rootProject.file("config/licenses/allowed.json")
    excludeBoms = true
    // Our own modules carry no POM license.
    excludeGroups = arrayOf("cairn\\..*", "cairn")
}
