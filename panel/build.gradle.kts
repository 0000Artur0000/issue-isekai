plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val frontendDir = layout.projectDirectory.dir("frontend")

val npmCi = tasks.register<Exec>("npmCi") {
    workingDir(frontendDir)
    commandLine("npm", "ci")
    inputs.files(frontendDir.file("package.json"), frontendDir.file("package-lock.json"))
    outputs.dir(frontendDir.dir("node_modules"))
}

val npmBuild = tasks.register<Exec>("npmBuild") {
    dependsOn(npmCi)
    workingDir(frontendDir)
    commandLine("npm", "run", "build")
    inputs.files(fileTree(frontendDir) { exclude("node_modules/**", "dist/**") })
    outputs.dir(frontendDir.dir("dist"))
}

tasks.processResources {
    from(npmBuild) { into("static") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
