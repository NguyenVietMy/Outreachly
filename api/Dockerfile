# ---------- Build stage ----------
    FROM maven:3.9-eclipse-temurin-17 AS build
    WORKDIR /workspace
    COPY pom.xml .
    COPY src ./src
    RUN mvn -q -DskipTests package
    
    # ---------- Run stage ----------
    FROM eclipse-temurin:17-jre
    WORKDIR /app
    # copy the built jar (use wildcard in case the jar name changes)
    COPY --from=build /workspace/target/*.jar app.jar
    EXPOSE 8080
    ENTRYPOINT ["java","-jar","/app/app.jar"]
    