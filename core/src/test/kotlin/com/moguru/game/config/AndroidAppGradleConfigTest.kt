package com.moguru.game.config

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidAppGradleConfigTest {
    @Test
    fun `android app keeps AGP built-in Kotlin enabled with Compose compiler plugin`() {
        val root = Path.of("..")
        val rootBuildScript = Files.readString(root.resolve("build.gradle.kts"))
        val buildScript = Files.readString(root.resolve("androidApp/build.gradle.kts"))
        val androidGradlePluginVersion = Regex(
            """id\("com\.android\.application"\)\s+version\s+"(\d+)\.""",
        ).find(rootBuildScript)?.groupValues?.get(1)?.toInt()

        assertTrue(
            androidGradlePluginVersion != null && androidGradlePluginVersion >= 9,
            "AGP built-in Kotlin requires com.android.application 9.0 or newer.",
        )
        assertTrue(
            buildScript.contains("enableKotlin = true"),
            "androidApp must keep AGP built-in Kotlin enabled so src/main/kotlin and src/test/kotlin are Android variant sources.",
        )
        assertTrue(
            buildScript.contains("""id("org.jetbrains.kotlin.plugin.compose")"""),
            "androidApp must keep applying the Compose compiler plugin for Compose compilation.",
        )
        assertFalse(
            buildScript.contains("""id("org.jetbrains.kotlin.android")"""),
            "AGP 9 built-in Kotlin rejects org.jetbrains.kotlin.android; use android.enableKotlin instead.",
        )
    }
}
