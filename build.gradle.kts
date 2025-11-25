plugins {
    java
}

allprojects {
    group = "p2p.antigravity"
    version = "0.1.0"
    
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    
    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    }
    
    tasks.test {
        useJUnitPlatform()
    }
}
