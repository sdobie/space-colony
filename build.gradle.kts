plugins {
    application
    java
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "spacecolony.Main"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("-ea")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    jvmArgs("-ea")
}

tasks.register<JavaExec>("render-demo") {
    group = "application"
    description = "Render one flat-map + sphere PNG per BodyType for visual inspection."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "spacecolony.render.RenderDemo"
}

tasks.register<JavaExec>("play") {
    group = "application"
    description = "Launch the Swing UI."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "spacecolony.SpaceColonyApp"
    standardInput = System.`in`
    jvmArgs("-ea")
}
