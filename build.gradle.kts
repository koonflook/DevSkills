plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val localPluginDir = layout.projectDirectory.dir("libs").asFile
val localPluginJars = files(
    localPluginDir.resolve("MMOCore.jar"),
    localPluginDir.resolve("MythicLib.jar"),
    localPluginDir.resolve("PlaceholderAPI.jar"),
    localPluginDir.resolve("ProtocolLib.jar"),
    localPluginDir.resolve("Floodgate.jar")
)

check(localPluginJars.files.all { it.isFile }) {
    "Missing local plugin dependencies in ${localPluginDir.absolutePath}"
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly(localPluginJars)
    implementation("net.objecthunter:exp4j:0.4.8")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.+")
    testImplementation(localPluginJars)
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from({
            configurations.runtimeClasspath.get()
                .filter { it.extension == "jar" }
                .map { zipTree(it) }
        })
    }

    test {
        useJUnitPlatform()
    }
}
