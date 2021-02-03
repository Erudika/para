# Default Para docker image bundled with H2 and Lucene plugins

FROM maven:3.6-jdk-11-slim AS build

RUN mkdir -p /para
RUN curl -Ls https://github.com/Erudika/para/archive/master.tar.gz | tar -xz -C /para
RUN cd /para/para-master && mvn -q install -DskipTests=true -DskipITs=true && \
	cd /para/para-master/para-jar && mv target/para-[0-9]*.jar /para/

FROM openjdk:11-jre-slim

ENV BOOT_SLEEP=0 \
    JAVA_OPTS=""

RUN mkdir -p /para/lib &&	mkdir -p /para/data

WORKDIR /para

VOLUME ["/para/data"]

COPY --from=build /para/para-*.jar /para/para.jar

EXPOSE 8080

CMD sleep $BOOT_SLEEP && \
    java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar para.jar
