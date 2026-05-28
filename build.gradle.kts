plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "com.moguru.game"
version = "0.1.0"

repositories {
    maven {
        url = uri("https://repo1.maven.org/maven2/")
    }
    mavenCentral()
}

dependencies {
    implementation(files("libs/jlayer-1.0.1.jar"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.moguru.game.gui.MoguraGameAppKt")
}

sourceSets {
    main {
        resources.srcDir("assets")
    }
}

kotlin {
    jvmToolchain(17)
}
