FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /app
RUN microdnf install -y findutils zlib
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/ gradle/
RUN ./gradlew dependencies --no-daemon
COPY src/ src/
RUN ./gradlew nativeCompile --no-daemon

FROM gcr.io/distroless/base-debian12
WORKDIR /app
COPY --from=build /app/build/native/nativeCompile/core ./core
COPY --from=build /usr/lib64/libz.so.1 /lib/x86_64-linux-gnu/libz.so.1
EXPOSE 8080
ENTRYPOINT ["./core"]
