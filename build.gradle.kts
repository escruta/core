plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.flywaydb.flyway") version "12.9.0"
}
val springAiVersion = "2.0.0"

group = "com.escruta"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

flyway {
    url = System.getenv("ESCRUTA_DB_URL") ?: "jdbc:mariadb://localhost:3306/escruta"
    user = System.getenv("ESCRUTA_DB_USER") ?: "root"
    password = System.getenv("ESCRUTA_DB_PASSWORD") ?: "1234"
}

repositories {
    mavenCentral()
}

configurations.all {
    exclude(group = "org.eclipse.angus", module = "angus-activation")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-qdrant")
    implementation("org.springframework.ai:spring-ai-vector-store-advisor")
    implementation("jakarta.activation:jakarta.activation-api:2.1.4")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.springframework.retry:spring-retry:2.0.13")
    implementation("org.springframework.ai:spring-ai-retry:1.1.2")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-mysql:12.9.0")
        classpath("org.mariadb.jdbc:mariadb-java-client:3.5.9")
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    mainClass.set("com.escruta.core.EscrutaCore")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

jacoco {
    toolVersion = "0.8.15"
}
