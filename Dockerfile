# 1.基础镜像：使用轻量级的 JDK 17
FROM openjdk:17-jdk-slim

# 2.维护者信息
LABEL maintainer="三文鱼"

# 3.设置工作目录
WORKDIR /app

# 4.暴露端口（Spring Boot 默认端口）
EXPOSE 8080

# 5.复制编译后的Jar包到容器里
# 注意：这里假设Jar包生成在target目录下
# app.jar是我们给它起的别名，方便后面启动
COPY target/*.jar app.jar

# 6.启动命令
# 这里的参数是为了加快启动速度，并指定时区
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]