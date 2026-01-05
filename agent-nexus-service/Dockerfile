# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# 配置 GitHub Packages 认证（从 GitHub Packages 拉取依赖）
ARG GITHUB_TOKEN
RUN mkdir -p /root/.m2 && \
    echo "<settings><servers><server><id>github</id><username>\${env.GITHUB_ACTOR}</username><password>\${env.GITHUB_TOKEN}</password></server></servers></settings>" > /root/.m2/settings.xml

# 复制父 POM 和 service 模块
COPY pom.xml .
COPY agent-nexus-service/pom.xml ./agent-nexus-service/
COPY agent-nexus-service/src ./agent-nexus-service/src

# 先安装父 POM 到本地仓库，再构建 service
RUN mvn install:install-file -Dfile=pom.xml -DpomFile=pom.xml && \
    cd agent-nexus-service && \
    mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/agent-nexus-service/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8003 20883
# Force deployment trigger
ENTRYPOINT ["java", "-jar", "app.jar"]
