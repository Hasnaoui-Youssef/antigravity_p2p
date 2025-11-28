plugins {
    application
}

dependencies {
    implementation(project(":common"))
    testImplementation(project(":bootstrap-server"))
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}

application {
    mainClass.set("p2p.peer.PeerNode")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "p2p.peer.PeerNode"
    }
    
    // To create a "fat jar" that includes all dependencies
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
