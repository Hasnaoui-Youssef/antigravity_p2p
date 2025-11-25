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
