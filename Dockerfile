FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first for better dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and docs
COPY src ./src
COPY docs ./docs

# Build the application
RUN mvn clean package -DskipTests

############################
# Runtime stage
############################
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy application JAR
COPY --from=build /app/target/*.jar app.jar

# Copy docs for TechAgent retrieval
COPY --from=build /app/docs ./docs

# Expose application port
EXPOSE 8080

# Environment variables (can be overridden at runtime)
ENV OPENAI_MODEL=gpt-4o-mini
ENV DOCS_PATH=/app/docs

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
