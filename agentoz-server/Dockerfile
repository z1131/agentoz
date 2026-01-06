# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# 准备 Maven settings，使用 BuildKit secret 注入 GitHub Packages token
ARG GITHUB_ACTOR
RUN --mount=type=secret,id=github_token \
    mkdir -p /root/.m2 && \
    cat > /root/.m2/settings.xml <<EOF
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>${GITHUB_ACTOR}</username>
      <password>$(cat /run/secrets/github_token)</password>
    </server>
  </servers>
</settings>
EOF

# 复制整个项目进行构建
COPY . .

# 构建 service 及其依赖模块 (如 agentoz-starter)
RUN --mount=type=secret,id=github_token mvn clean package -DskipTests -pl agentoz-server -am --batch-mode --errors

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/agentoz-server/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8003 20883
# Force deployment trigger
ENTRYPOINT ["java", "-jar", "app.jar"]
