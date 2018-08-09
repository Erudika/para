# Para docker image without any plugins

FROM openjdk:8-jdk-alpine

RUN apk --update add git openssh maven && \
    rm -rf /var/lib/apt/lists/* && \
    rm /var/cache/apk/*

ENV BOOT_SLEEP=0 \
    JAVA_OPTS=""

RUN addgroup -S para && adduser -S -G para para && \
	mkdir -p /para/lib && \
	mkdir -p /para/data && \
	mkdir -p /para/clone && \
	chown -R para /para

USER para

WORKDIR /para

RUN git clone --depth=1 https://github.com/Erudika/para /para/clone && \
	cd /para/clone && \
	mvn install -DskipTests=true -DskipITs=true && \
	cd /para/clone/para-jar && \
	mvn -Pbase clean package && \
	mv target/para-base-*.jar /para/ && \
	cd /para &&	rm -rf /para/clone && rm -rf ~/.m2


VOLUME ["/para/data"]

EXPOSE 8080

CMD sleep $BOOT_SLEEP && \
    java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar para-base-*.jar
