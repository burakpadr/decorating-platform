# Build context is the repo root (see infra/docker-compose.yml) so shared files resolve.
#   docker build -f api/Dockerfile -t decorating-api .

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Wrapper and POM first: dependency resolution is cached until the POM changes.
COPY api/mvnw ./
COPY api/.mvn ./.mvn
COPY api/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY api/src ./src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

# *.jar excludes the plain, non-executable jar the repackage goal leaves behind as .jar.original.
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
