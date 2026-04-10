FROM gradle:9.1.0-jdk25-alpine AS build
WORKDIR /app

COPY . .
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["sh", "-c", "java --enable-native-access=ALL-UNNAMED -jar app.jar --server.port=${PORT:-8080}"]
