#!/bin/csh


set opt="-DOSMOT_CONFIG=$home/arxiv/arxiv"

set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"

set opt="-cp ${cp} ${opt}"

#echo "opt=$opt"

java $opt edu.rutgers.axs.ee5.LowDimDocumentExporter $argv


