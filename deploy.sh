#!/bin/bash
lastver=$(git describe --abbrev=0 --tags)
echo "Last tag was: $lastver"
echo "---"
read -e -p "New version: " ver
read -e -p "New dev version: " devver
#sed -i -e "s/PARA_VERSION=.*/PARA_VERSION="\"$ver\""/g" Dockerfile Dockerfile-base && \
sed -i -e "s/$lastver/$ver/g" installer.sh
git add -A && git commit -m "Release v$ver." && git push origin master

mvn --batch-mode -Dtag=v${ver} release:prepare -Dresume=false -DreleaseVersion=${ver} -DdevelopmentVersion=${devver}-SNAPSHOT && \
mvn release:perform && \
echo "Maven release done, publishing release on GitHub..," && \
git log $lastver..HEAD --oneline >> changelog.txt && \
echo "" >> changelog.txt && \
echo "### :package: [Download JAR](https://repo1.maven.org/maven2/com/erudika/para-server/${ver}/para-server-${ver}.jar)" >> changelog.txt && \
gh release create -F changelog.txt -t "v$ver" "v$ver" && \
rm changelog.txt


