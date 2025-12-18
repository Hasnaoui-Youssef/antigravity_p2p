plugins {
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
}

application {
    mainClass.set("p2p.peer.peerui.PeerUIApplication")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":peer-node"))
    implementation(project(":common"))
}
tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "p2p.peer.peerui.PeerUIApplication"
    }

    // To create a "fat jar" that includes all dependencies
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
