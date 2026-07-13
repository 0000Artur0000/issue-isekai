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
}
