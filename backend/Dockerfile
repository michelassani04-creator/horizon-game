# Étape 1 : compilation
FROM gradle:8.9-jdk17 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src
RUN gradle buildFatJar --no-daemon -x test

# Étape 2 : image d'exécution légère, utilisateur non-root
FROM eclipse-temurin:17-jre
RUN useradd --system --create-home horizon
WORKDIR /app
COPY --from=build /src/build/libs/horizon-backend.jar /app/horizon-backend.jar
USER horizon
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-Xss512k", "-jar", "/app/horizon-backend.jar"]
