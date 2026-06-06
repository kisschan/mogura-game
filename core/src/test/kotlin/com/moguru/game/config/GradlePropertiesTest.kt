package com.moguru.game.config

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GradlePropertiesTest {
    @Test
    fun `repository gradle properties keep checked in truststore and proxy settings`() {
        val root = Path.of("..")
        val gradleProperties = Properties().apply {
            Files.newInputStream(root.resolve("gradle.properties")).use(::load)
        }
        val jvmArgs = gradleProperties.getProperty("org.gradle.jvmargs")

        assertTrue(Files.isRegularFile(root.resolve("gradle/certs/gradle-truststore.p12")))
        assertEquals("gradle/certs/gradle-truststore.p12", gradleProperties.getProperty("systemProp.javax.net.ssl.trustStore"))
        assertEquals("PKCS12", gradleProperties.getProperty("systemProp.javax.net.ssl.trustStoreType"))
        assertEquals("changeit", gradleProperties.getProperty("systemProp.javax.net.ssl.trustStorePassword"))
        assertEquals("true", gradleProperties.getProperty("systemProp.java.net.useSystemProxies"))
        assertTrue(jvmArgs.contains("-Djavax.net.ssl.trustStore=gradle/certs/gradle-truststore.p12"))
        assertTrue(jvmArgs.contains("-Djavax.net.ssl.trustStoreType=PKCS12"))
        assertTrue(jvmArgs.contains("-Djavax.net.ssl.trustStorePassword=changeit"))
        assertTrue(jvmArgs.contains("-Djava.net.useSystemProxies=true"))
        assertTrue(jvmArgs.contains("-Dfile.encoding=UTF-8"))
    }
}
