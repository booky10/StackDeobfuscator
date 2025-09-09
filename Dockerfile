FROM eclipse-temurin:21-jdk AS builder

COPY ./ /work
WORKDIR /work

RUN apt-get update && apt-get install -y git && \
        ./gradlew --no-daemon :StackDeobfuscator-Web:shadowJar -PnoFabric

FROM eclipse-temurin:21-jre

COPY --from=builder /work/build/libs/*-all.jar /usr/local/lib/stackdeobf.jar
WORKDIR /app

ENV HASTEBIN_API_TOKEN=""
EXPOSE 8080

ENTRYPOINT ["/usr/bin/env"]
CMD ["/opt/java/openjdk/bin/java", "-XX:MaxRAMPercentage=95.0", "-Dweb.bind=0.0.0.0", "-Dweb.port=8080", "-jar", "/usr/local/lib/stackdeobf.jar"]
