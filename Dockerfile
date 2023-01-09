FROM openjdk:17-slim

RUN mkdir /app

COPY ./build/install/File-Portal/lib/* /app/lib/

WORKDIR /app

EXPOSE 8080

CMD ["java", "-server", \
    "-XX:+UseContainerSupport", \
    "-XX:+UseG1GC", \
    "-Xmx3g", \
    "-XX:MaxGCPauseMillis=150", \
    "-classpath", "lib/*", \
    "com.fileportal.portal.FilePortalApplication"]
