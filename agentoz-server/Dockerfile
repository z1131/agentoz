# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .

# 极简 settings
ARG GITHUB_ACTOR
RUN --mount=type=secret,id=github_token \
    mkdir -p /root/.m2 && \
    cat > /root/.m2/settings.xml <<EOF
<settings>
  <mirrors>
      <mirror>
        <id>aliyunmaven</id>
        <mirrorOf>*</mirrorOf>
        <name>阿里云公共仓库</name>
        <url>https://maven.aliyun.com/repository/public</url>
      </mirror>
  </mirrors>
  <servers>
    <server>
      <id>github-agentoz</id>
      <username>${GITHUB_ACTOR}</username>
      <password>$(cat /run/secrets/github_token)</password>
    </server>
  </servers>
</settings>
EOF

# 仅构建启动模块，直接指定 settings
RUN --mount=type=secret,id=github_token mvn clean package -DskipTests -pl agentoz-server -am -s /root/.m2/settings.xml

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/agentoz-server/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8003 20883
ENTRYPOINT ["java", "-jar", "app.jar"]