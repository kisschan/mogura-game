plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(files("../libs/jlayer-1.0.1.jar"))

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
