# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# 配置 GitHub Packages 认证（从 GitHub Packages 拉取依赖）
ARG GITHUB_TOKEN
RUN mkdir -p /root/.m2 && \
    echo "<settings><servers><server><id>github</id><username>\${env.GITHUB_ACTOR}</username><password>\${env.GITHUB_TOKEN}</password></server></servers></settings>" > /root/.m2/settings.xml

# 复制整个项目进行构建
COPY . .

# 构建 service 及其依赖模块 (如 agent-nexus-api)
RUN mvn clean package -DskipTests -pl agent-nexus-service -am

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/agent-nexus-service/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8003 20883
# Force deployment trigger
ENTRYPOINT ["java", "-jar", "app.jar"]
