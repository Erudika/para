FROM maven:eclipse-temurin AS deps

ENV PARA_HOME=/para
WORKDIR ${PARA_HOME}

# Cache dependencies for the para-jar module
COPY pom.xml pom.xml
COPY para-core/pom.xml para-core/pom.xml
COPY para-client/pom.xml para-client/pom.xml
COPY para-server/pom.xml para-server/pom.xml
COPY para-jar/pom.xml para-jar/pom.xml
RUN mvn -B dependency:go-offline --fail-never

FROM deps AS build
# Para docker image without any plugins
ENV PARA_HOME=/para
WORKDIR ${PARA_HOME}

COPY . .
RUN mvn -B -pl para-jar -am -DskipTests=true -DskipITs=true package && \
    cp para-jar/target/para-[0-9]*.jar ${PARA_HOME}/para.jar

FROM eclipse-temurin:25-jre-alpine

ENV PARA_HOME=/para
ENV JAVA_OPTS="-Dloader.path=lib"
WORKDIR ${PARA_HOME}

COPY --from=build ${PARA_HOME}/para.jar ./para.jar

RUN mkdir -p ${PARA_HOME}/lib ${PARA_HOME}/data
VOLUME ["/para/data", "/para/lib"]

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar para.jar \"$@\"", "para"]
CMD []
