# Default Para docker image bundled with H2 and Lucene plugins

FROM openjdk:8-jdk-alpine

ENV BOOT_SLEEP=0 \
    JAVA_OPTS="" \
    PARA_VERSION="1.31.3" \
    REPO_URL=https://oss.sonatype.org/service/local/repositories/releases/content/com/erudika

RUN addgroup -S para && adduser -S -G para para && \
	mkdir -p /para/lib && \
	mkdir -p /para/data && \
	chown -R para:para /para

USER para

WORKDIR /para

RUN wget -q -P /para/ $REPO_URL/para-jar/$PARA_VERSION/para-jar-$PARA_VERSION.jar

VOLUME ["/para/data"]

EXPOSE 8080

CMD sleep $BOOT_SLEEP && \
    java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar para-*.jar
