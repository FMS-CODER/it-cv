FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 国内服务器直连 Maven 中央仓库极慢，改用阿里云源
# （注意：Docker 镜像加速器只代理容器镜像，不覆盖 Maven 的 jar 包下载）
COPY settings.xml /build/settings.xml
COPY pom.xml .

# 1) 限制 Maven 堆内存，避免 2G 小机器构建时 OOM 卡死
# 2) cache 挂载复用 ~/.m2 依赖，重复构建不再重新下载
RUN --mount=type=cache,target=/root/.m2/repository \
    MAVEN_OPTS="-Xmx1G -Xms512m" mvn -s /build/settings.xml -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    MAVEN_OPTS="-Xmx1G -Xms512m" mvn -s /build/settings.xml -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
