# Build estágio — Maven + JDK 17
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests package

# Runtime — só JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# curl: healthcheck do Docker Compose (wget não vem por defeito nesta imagem)
RUN apk add --no-cache curl \
    && addgroup -S spring && adduser -S spring -G spring
COPY --from=build /build/target/consumo-esperto-backend-*.jar /app/app.jar
USER spring:spring
# Porta efectiva definida por SERVER_PORT (ex.: 8087 no compose)
EXPOSE 8087
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
