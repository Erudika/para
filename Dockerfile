FROM maven:eclipse-temurin AS deps

ARG BUILD_OPTS=""
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
RUN mvn -B -pl para-jar -am ${BUILD_OPTS} -DskipTests=true -DskipITs=true package && \
    cp para-jar/target/para-[0-9]*.jar ${PARA_HOME}/para.jar

FROM eclipse-temurin:25-jre

ENV PARA_HOME=/para
ENV JAVA_OPTS="-Dloader.path=lib --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED"
WORKDIR ${PARA_HOME}

COPY --from=build ${PARA_HOME}/para.jar ./para.jar

RUN mkdir -p ${PARA_HOME}/lib ${PARA_HOME}/data
VOLUME ["${PARA_HOME}/data", "${PARA_HOME}/lib"]

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar para.jar \"$@\"", "para"]
CMD []
