plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
    // Unit-test configurations are excluded from dependency locking: they are
    // only used locally and would otherwise require hand-editing the lockfile.
    // Registered after lockAllConfigurations() so it wins for both existing
    // and future configurations.
    configurations.configureEach {
        if (name.contains("Test")) {
            resolutionStrategy.deactivateDependencyLocking()
        }
    }
}
