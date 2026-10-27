FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -g 10001 app && adduser -D -u 10001 -G app app && mkdir -p uploads && chown -R app:app /app
COPY --from=build /build/target/aged-care-backend-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
