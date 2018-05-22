FROM openjdk:8-jre-alpine

ENV BOOT_SLEEP=0 \
    JAVA_OPTS="" \
    PARA_VERSION=1.29.1 \
	REPO_URL=https://oss.sonatype.org/service/local/repositories/releases/content/com/erudika

RUN addgroup -S para && adduser -S -G para para && \
	mkdir -p /para/lib && \
	mkdir -p /para/data && \
	chown -R para /para

USER para

WORKDIR /para

RUN wget -q -P /para/ $REPO_URL/para-war/$PARA_VERSION/para-war-$PARA_VERSION.war

VOLUME ["/para/data"]

EXPOSE 8080

CMD sleep ${BOOT_SLEEP} && \
    java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar para-*.war
