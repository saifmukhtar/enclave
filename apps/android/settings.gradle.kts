pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    if (providers.gradleProperty("fdroid").orNull == "true" || System.getenv("FDROID") == "true") {
        repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    } else {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    }
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Enclave"
include(":app")

if (providers.gradleProperty("fdroid").orNull == "true" || System.getenv("FDROID") == "true") {
    include(":client")
    project(":client").projectDir = file("../../libsignal-src/java/client")
    include(":android")
    project(":android").projectDir = file("../../libsignal-src/java/android")
}
