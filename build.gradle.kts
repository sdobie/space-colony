plugins {
    application
    java
}

version = "0.6.0"

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

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("spacecolony/version.properties") { expand("version" to project.version) }
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
    description = "Launch the game: splash, title screen, then a game (--seed N skips to a game)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "spacecolony.SpaceColonyApp"
    standardInput = System.`in`
    jvmArgs("-ea")
    // `./gradlew play --debug` sets Gradle's own log level and never reaches the app, so
    // `-Pdebug` is the Gradle-side spelling of the app's --debug flag.
    if (project.hasProperty("debug")) args("--debug")
}

tasks.register<JavaExec>("debugPlayTest") {
    group = "verification"
    description = "Drive debug mode (Ctrl+D, overlay, step, inspector, log viewer) in the real window."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "spacecolony.playtest.DebugModeDriver"
    jvmArgs("-ea")
}

/**
 * Plan 6 startup play-test: splash, title, Options, the whole tutorial, Save As, Main Menu.
 * Options and saves live under build/playtest/startup, wiped at the start of each run.
 */
tasks.register<JavaExec>("startupPlayTest") {
    group = "verification"
    description = "Drive the real app from launch through the tutorial and back to the title."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "spacecolony.playtest.StartupDriver"
    jvmArgs("-ea")
    val dir = layout.buildDirectory.dir("playtest/startup").get().asFile
    systemProperty("spacecolony.savesDir", File(dir, "saves").absolutePath)
    systemProperty("spacecolony.optionsFile", File(dir, "options.properties").absolutePath)
    systemProperty("user.home", File(dir, "home").absolutePath)
    doFirst { dir.deleteRecursively(); dir.mkdirs() }
}
