package com.moguru.game.config

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class GradlePropertiesTest {
    @Test
    fun `repository gradle properties do not force a local truststore`() {
        val gradleProperties = Files.readString(Path.of("..", "gradle.properties"))

        assertFalse(gradleProperties.contains("javax.net.ssl.trustStore"))
        assertFalse(gradleProperties.contains("gradle/certs"))
        assertFalse(gradleProperties.contains("trustStorePassword"))
    }
}
