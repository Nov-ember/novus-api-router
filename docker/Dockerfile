FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /build

COPY pom.xml ./
COPY mvnw ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /build/target/novus-api-router-0.0.1-SNAPSHOT.jar ./app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]