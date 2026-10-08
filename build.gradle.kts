plugins {
    id("java")
    id("application")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("TopSecret")
}

// Gradle does not forward the terminal to the run task by default, which left
// the login prompt and the menu unable to read anything under ./gradlew run.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.jar {
    archiveFileName.set("TopSecret.jar")
    manifest {
        attributes["Main-Class"] = "TopSecret"
    }

    // Bundle the SQLite driver so `java -jar TopSecret.jar` works on its own.
    // Without this the jar starts but cannot open the database.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
}
