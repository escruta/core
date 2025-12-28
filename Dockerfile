FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /app
RUN microdnf install -y findutils
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/ gradle/
RUN ./gradlew dependencies --no-daemon
COPY src/ src/
RUN ./gradlew nativeCompile --no-daemon

FROM alpine:3
RUN apk add --no-cache gcompat
WORKDIR /app
COPY --from=build /app/build/native/nativeCompile/core ./core
EXPOSE 8080
ENTRYPOINT ["./core"]
