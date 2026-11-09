plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation("javazoom:jlayer:1.0.1")

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
        resources.srcDir("../assets")
    }
}

kotlin {
    jvmToolchain(17)
}
