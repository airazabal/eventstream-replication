# Use an official OpenJDK base image
FROM openjdk:11-jre-slim

# Set the working directory in the container
WORKDIR /app

# Copy the built JAR file and dependencies from the target directory
COPY target/event-streams-app-1.0-SNAPSHOT-jar-with-dependencies.jar /app/app.jar
COPY target/libs /app/libs

# Expose any necessary ports (if applicable)
# EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-cp", "app.jar:libs/*", "com.example.EventStreamProcessor"]
