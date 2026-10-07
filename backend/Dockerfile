# ---------------------------------------------------------------------------
# EasyOA API 镜像：Maven 构建 → JRE 21 运行（非 root 用户）
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先复制依赖描述文件，充分利用 Docker 层缓存
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
RUN ./mvnw -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q -DskipTests package \
    && cp target/easyoa-api-*.jar /build/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

# 健康检查依赖
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 easyoa \
    && useradd --system --uid 1001 --gid easyoa --create-home easyoa \
    && mkdir -p /var/lib/easyoa/storage \
    && chown -R easyoa:easyoa /var/lib/easyoa

COPY --from=build --chown=easyoa:easyoa /build/app.jar /app/app.jar

ENV TZ=UTC \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC -Dfile.encoding=UTF-8"

USER easyoa
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=10 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]