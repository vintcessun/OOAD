# 运行镜像：只装 JRE 与已构建好的 jar。
#
# 为什么不在镜像里编译：课程服务器是 2 核 2G，编译服务器已装好 JDK 21 + Maven 3.9.9，
# 先 `./mvnw -s deploy/maven-settings-huawei.xml verify` 出 jar（顺带跑完测试和 Jacoco），
# 再打镜像，既省一次 500MB 的 Maven 镜像下载，也保证进镜像的 jar 一定是测过的那个。
#
# 用法（在仓库根目录）：
#   docker build --build-arg MODULE=rbac-structured -t rbac-structured:latest .
#   docker build --build-arg MODULE=rbac-mock-biz   -t rbac-mock-biz:latest   .
ARG BASE_IMAGE=eclipse-temurin:21-jre
FROM ${BASE_IMAGE}

ARG MODULE
WORKDIR /app
COPY ${MODULE}/target/${MODULE}.jar app.jar

# 2G 内存的机器上同时跑 MySQL / Redis 时，堆必须封顶；部署时可用环境变量覆盖
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC" \
    TZ=Asia/Shanghai

EXPOSE 8081 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
