#!/bin/bash
set -e -x

# Lightsail/DigitalOcean installer script for Ubuntu
VERSION="1.50.2"
PORT="8080"
WORKDIR="/home/ubuntu/para"
mkdir $WORKDIR

JARURL="https://repo1.maven.org/maven2/com/erudika/para-jar/${VERSION}/para-jar-${VERSION}.jar"
sfile="/etc/systemd/system/para.service"

apt-get update && apt-get install -y wget openjdk-21-jre &&
wget -O para.jar ${JARURL} && \
mv para.jar $WORKDIR && \
chown ubuntu:ubuntu ${WORKDIR}/para.jar && \
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
ExecStart=java -jar -Dconfig.file=application.conf para.jar
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
