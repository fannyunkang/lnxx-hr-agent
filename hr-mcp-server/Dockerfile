FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
COPY hr-mcp-server/pom.xml hr-mcp-server/pom.xml
COPY hr-mcp-server/src hr-mcp-server/src
RUN mvn -pl hr-mcp-server -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/hr-mcp-server/target/hr-mcp-server-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
