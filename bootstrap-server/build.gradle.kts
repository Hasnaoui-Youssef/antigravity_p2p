plugins {
    application
}

dependencies {
    implementation(project(":common"))
}

application {
    mainClass.set("p2p.bootstrap.BootstrapServer")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "p2p.bootstrap.BootstrapServer"
    }
    
    // To create a "fat jar" that includes all dependencies
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
