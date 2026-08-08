FROM gradle:jdk25-noble AS builder
WORKDIR /builder

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/ gradle/
RUN ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew bootJar --no-daemon

FROM bellsoft/liberica-openjre-debian:25-cds
WORKDIR /application

COPY --from=builder /builder/build/libs/*.jar core.jar

RUN java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
    -XX:AOTCacheOutput=app.aot \
    -Dspring.context.exit=onRefresh \
    -Dspring.jpa.hibernate.ddl-auto=none \
    -Dspring.ai.vectorstore.type=none \
    -jar core.jar

EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow", "-XX:AOTCache=app.aot", "-jar", "core.jar"]
