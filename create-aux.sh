#!/bin/csh

#-- Creates auxiliary tables (ActionWeight)


set opt="-DOSMOT_CONFIG=$home/arxiv/arxiv"

set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar



set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

set opt="-cp ${cp} ${opt}"

echo "opt=$opt"

java $opt edu.rutgers.axs.sql.CreateAux

# /usr/lib/jvm/java-6-openjdk-amd64/bin/java  $opt edu.rutgers.axs.sql.CreateAux
