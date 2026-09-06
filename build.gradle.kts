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

/**
 * GUI play-test harness (Plan 4, Task 19 Step 4). Needs a desktop session: it shows a real
 * window and drives the real menus and dialogs. Screenshots land in build/playtest.
 */
tasks.register<JavaExec>("playTest") {
    group = "verification"
    description = "Drive the Swing UI through the Plan 4 manual play-test checklist."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "spacecolony.playtest.PlayTestDriver"
    jvmArgs("-ea")
}

tasks.register<JavaExec>("cacheCheck") {
    group = "verification"
    description = "Verify the per-body flat-map cache is invalidated on WorldReplaced."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "spacecolony.playtest.CacheDriver"
    jvmArgs("-ea")
}

tasks.register<JavaExec>("play") {
    group = "application"
    description = "Launch the Swing UI."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "spacecolony.SpaceColonyApp"
    standardInput = System.`in`
    jvmArgs("-ea")
}
