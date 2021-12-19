#!/bin/bash

# Run this script from inside the Para ./data folder, or wherever para.mv.db is located

OLD_H2_VERSION="1.4.200"
NEW_H2_VERSION="2.0.202"
PARA_USER="para"
PARA_PASS="secret"

wget -q -O h2-$NEW_H2_VERSION.jar https://search.maven.org/remotecontent?filepath=com/h2database/h2/2.0.202/h2-2.0.202.jar && \
wget -q -O h2-$OLD_H2_VERSION.jar https://search.maven.org/remotecontent?filepath=com/h2database/h2/1.4.200/h2-1.4.200.jar && \

java -cp h2-${OLD_H2_VERSION}.jar org.h2.tools.Script -url jdbc:h2:./para -user $PARA_USER -password $PARA_PASS -script para.sql && \
rm para.mv.db para.trace.db && \
java -cp h2-${NEW_H2_VERSION}.jar org.h2.tools.RunScript -url jdbc:h2:./para -user $PARA_USER -password $PARA_PASS -script para.sql && \
rm h2-$NEW_H2_VERSION.jar h2-$OLD_H2_VERSION.jar && \

echo "Migrated Para DB from $OLD_H2_VERSION to $NEW_H2_VERSION." || \

echo "Migration failed."