
# ---------- BUILD STAGE ----------
FROM gradle:8.0-jdk17 AS build

WORKDIR /app

COPY --chown=gradle:gradle . .

# Ensure the Gradle user owns the working directory itself
RUN chown -R gradle:gradle /app

USER gradle

RUN gradle --no-daemon bootJar -x test

# ---------- RUN STAGE ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
