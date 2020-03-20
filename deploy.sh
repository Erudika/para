#!/bin/bash
lastver=$(git describe --abbrev=0 --tags)
echo "Last tag was: $lastver"
echo "---"
read -e -p "New version: " ver
read -e -p "New dev version: " devver

sed -i -e "s/PARA_VERSION=.*/PARA_VERSION="\"$ver\""/g" Dockerfile Dockerfile-base && \

git add -A && git commit -m "Release v$ver." && git push origin master && \

mvn --batch-mode -Dtag=v${ver} release:prepare -DreleaseVersion=${ver} -DdevelopmentVersion=${devver}-SNAPSHOT && \
mvn release:perform && \
echo "Maven release done, publishing release on GitHub..," && \
echo "v$ver" > changelog.txt && \
echo "" >> changelog.txt && \
git log $lastver..HEAD --oneline >> changelog.txt && \
echo "" >> changelog.txt && \
echo "### :package: [Download JAR](https://oss.sonatype.org/service/local/repositories/releases/content/com/erudika/para-jar/${ver}/para-jar-${ver}.jar)" >> changelog.txt && \
echo "" >> changelog.txt && \
echo "### :package: [Download WAR](https://oss.sonatype.org/service/local/repositories/releases/content/com/erudika/para-war/${ver}/para-war-${ver}.war)" >> changelog.txt && \
hub release create -F changelog.txt "v$ver" && \
rm changelog.txt


