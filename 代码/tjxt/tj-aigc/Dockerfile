FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY . .
RUN mvn -B -ntp -pl tj-aigc -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=dev-demo

WORKDIR /app
RUN ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime && echo ${TZ} > /etc/timezone

COPY --from=builder /build/tj-aigc/target/tj-aigc.jar /app/app.jar

EXPOSE 8094

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev-demo}"]
