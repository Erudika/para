#!/bin/bash
lastver=$(git describe --abbrev=0 --tags)
echo "Last tag was: $lastver"
echo "---"
read -e -p "New version: " ver
read -e -p "New dev version: " devver
#sed -i -e "s/PARA_VERSION=.*/PARA_VERSION="\"$ver\""/g" Dockerfile Dockerfile-base && \
sed -i -e "s/$lastver/$ver/g" installer.sh
git add -A && git commit -m "Release $ver." && git push origin master

mvn --batch-mode -Dtag=${ver} release:prepare -Dresume=false -DreleaseVersion=${ver} -DdevelopmentVersion=${devver}-SNAPSHOT && \
mvn release:perform && \
echo "Maven release done, publishing release on GitHub..," && \
git log $lastver..HEAD --oneline >> changelog.txt && \
echo "" >> changelog.txt && \
echo "### :package: [Download executable JAR](https://github.com/Erudika/para/releases/download/${ver}/para-${ver}.jar)" >> changelog.txt && \
gh release create -F changelog.txt -t "$ver" "$ver" && \
rm changelog.txt

# Build executable JAR
mvn -DskipTests=true -Pfatjar,sql,lucene package
mv para-server/target/classes/META-INF/sbom/application.cdx.json para-server/target/para-$ver-cyclonedx.json
gh release upload $ver target/para-$ver-cyclonedx.json
gh release upload $ver target/para-$ver.jar

