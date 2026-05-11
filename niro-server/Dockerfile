# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
# 增加 Maven 内存限制，防止编译时 OOM
ENV MAVEN_OPTS="-Xmx1024m"
COPY pom.xml .
COPY niro-sdk/pom.xml niro-sdk/
COPY niro-core/pom.xml niro-core/
COPY niro-web/pom.xml niro-web/
# 预下载依赖
RUN mvn dependency:go-offline -B

COPY niro-sdk/src niro-sdk/src
COPY niro-core/src niro-core/src
COPY niro-web/src niro-web/src
RUN mvn clean package -Dmaven.test.skip=true

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 安装时区数据并设置上海时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

COPY --from=build /app/niro-web/target/*.jar app.jar
EXPOSE 8080
# 生产环境使用 prod profile (改为由外部环境变量控制)
ENTRYPOINT ["java", "-jar", "app.jar"]
