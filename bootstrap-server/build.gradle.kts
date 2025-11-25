plugins {
    application
}

dependencies {
    implementation(project(":common"))
}

application {
    mainClass.set("p2p.bootstrap.BootstrapServer")
}
