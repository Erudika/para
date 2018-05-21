FROM openjdk:8-jre-alpine

ENV BOOT_SLEEP=0 \
    JAVA_OPTS=""

RUN addgroup -S para && adduser -S -G para para && \
	mkdir -p /para/lib && \
	mkdir -p /para/data && \
	chown -R para /para

USER para

WORKDIR /para

ADD para-war/target/para-*.war /para/

VOLUME ["/para/data"]

EXPOSE 8080

CMD sleep ${BOOT_SLEEP} && \
    java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar para-*.war
