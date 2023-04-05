plugins {
    id("java")
}

group = "exqudens"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.26")
    annotationProcessor("org.projectlombok:lombok:1.18.26")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("org.testcontainers:testcontainers:1.17.6")
    testCompileOnly("org.projectlombok:lombok:1.18.26")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.26")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("ch.qos.logback:logback-classic:1.4.6")
    testImplementation("com.zaxxer:HikariCP:5.0.1")
    testImplementation("org.postgresql:postgresql:42.6.0")
}

tasks.test {
    useJUnitPlatform()
}