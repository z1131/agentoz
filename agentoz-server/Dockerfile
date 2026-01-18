# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Copy root pom
COPY pom.xml .

# 2. Copy modules
COPY agentoz-api agentoz-api
COPY agentoz-server agentoz-server
COPY agentoz-starter agentoz-starter

# 3. Build settings
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
RUN mkdir -p /root/.m2 && \
    cat > /root/.m2/settings.xml <<EOF
<settings>
  <mirrors>
      <mirror>
        <id>aliyunmaven</id>
        <mirrorOf>*,!github-agentoz</mirrorOf>
        <name>阿里云公共仓库</name>
        <url>https://maven.aliyun.com/repository/public</url>
      </mirror>
  </mirrors>
  <servers>
    <server>
      <id>github-agentoz</id>
      <username>${GITHUB_ACTOR}</username>
      <password>${GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
EOF

# 4. Build
# 注意：不需要 mount secret，直接用 ARG 传入 token 更通用（或者您保持原有 secret 方式也可，但我先用通用写法）
# 如果您的环境强依赖 secret挂载，请告诉我。这里我先用简单变量。
RUN mvn clean package -DskipTests -pl agentoz-server -am -s /root/.m2/settings.xml

# Stage 2: Run
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/agentoz-server/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8003 20883
ENTRYPOINT ["java", "-jar", "app.jar"]
