#!/bin/bash
set -e -x

# Lightsail/DigitalOcean installer script for Ubuntu
VERSION="1.50.2"
PORT="8080"
WORKDIR="/home/ubuntu/para"
mkdir -p $WORKDIR/lib

# Use the GitHub release asset, not the Maven Central artifact. The release JAR is built with the
# `fatjar` profile, which gives it a Spring Boot PropertiesLauncher and bakes in the default
# plugins (SQL and Lucene). The Maven Central artifact is a plain JarLauncher with no plugins and
# ignores -Dloader.path, so it cannot load anything from lib/.
JARURL="https://github.com/Erudika/para/releases/download/${VERSION}/para-${VERSION}.jar"
sfile="/etc/systemd/system/para.service"

apt-get update && apt-get install -y wget openjdk-21-jre &&
wget -O para.jar ${JARURL} && \
mv para.jar $WORKDIR && \
chown -R ubuntu:ubuntu ${WORKDIR} && \
chmod +x ${WORKDIR}/para.jar
touch ${WORKDIR}/application.conf && \
chown ubuntu:ubuntu ${WORKDIR}/application.conf

# Feel free to modify the Para configuration here
cat << EOF > ${WORKDIR}/application.conf
para.app_name = "Para"
para.port = 8080
para.env = "production"
EOF

touch $sfile
cat << EOF > $sfile
[Unit]
Description=Para
After=syslog.target
StartLimitIntervalSec=30
StartLimitBurst=2
[Service]
WorkingDirectory=${WORKDIR}
SyslogIdentifier=Para
# JVM -D flags must precede -jar, otherwise the JVM treats them as arguments to Para.
# -Dloader.path=lib puts the lib/ folder on the classpath; this only works because the release
# JAR uses PropertiesLauncher. Put JDBC drivers there, e.g. lib/mysql-connector-j.jar.
ExecStart=java -Dconfig.file=application.conf -Dloader.path=lib -jar para.jar
User=ubuntu
Restart=on-failure
RestartSec=1s
[Install]
WantedBy=multi-user.target
EOF

# This is optional. These rules might interfere with other web server configurations like nginx and certbot.
#iptables -t nat -A PREROUTING -p tcp -m tcp --dport 80 -j REDIRECT --to-port ${PORT} && \
#iptables -t nat -A OUTPUT -p tcp --dport 80 -o lo -j REDIRECT --to-port ${PORT}

systemctl enable para.service && \
systemctl start para.service
