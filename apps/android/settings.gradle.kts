// The native Android app for the people who run a business on OpenTurnly.
// Identity (name, application id, version, colour, hosted preset, links) comes
// from brand/brand.properties, so a publisher swaps that one file and the icon
// set and never edits this build or the sources.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "OpenTurnly"
include(":app")
