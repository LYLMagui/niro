# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY niro-core/pom.xml niro-core/
COPY niro-web/pom.xml niro-web/
# 预下载依赖 (可选，加速后续构建)
RUN mvn dependency:go-offline -B
COPY niro-core/src niro-core/src
COPY niro-web/src niro-web/src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/niro-web/target/*.jar app.jar
EXPOSE 8080
# 生产环境使用 prod profile
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
