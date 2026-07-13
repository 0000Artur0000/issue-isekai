repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.citizensnpcs.co/repo")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("com.denizenscript:denizen:1.3.3-SNAPSHOT") {
        isChanging = false
        isTransitive = false
    }
    testImplementation("com.google.code.gson:gson:2.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
