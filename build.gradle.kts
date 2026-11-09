import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    kotlin("jvm") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.android.application") version "9.2.0" apply false
}

group = "com.moguru.game"
version = "0.1.0"

subprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}

// Resolve artifacts as well as graphs so verification covers every build variant.
tasks.register("resolveAndLockAll") {
    group = "verification"
    description = "Resolve all project configurations when deliberately updating dependency locks."
    notCompatibleWithConfigurationCache("Resolves all project configurations")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "Run with --write-locks; review the resulting locks and verification metadata."
        }
    }
    doLast {
        allprojects.forEach { target ->
            target.configurations.filter { it.isCanBeResolved }.forEach { configuration ->
                // AGP project variants have several output artifact types. Only external
                // modules need verification; the full graph still supplies the lock state.
                configuration.incoming.artifactView {
                    componentFilter { it is ModuleComponentIdentifier }
                }.files.files
            }
        }
    }
}
