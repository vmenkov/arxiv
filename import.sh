#!/bin/csh

set opt="-DOSMOT_CONFIG=."

set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"


#set opt="-cp ${cp} ${opt} -Drewrite=false"
set opt="-cp ${cp} ${opt} -Drewrite=true -Doptimize=true -Ddays=3"
# -Dfrom=2012-01-16

echo "opt=$opt"

#echo java $opt edu.cornell.cs.osmot.indexer.Indexer $1 $2 $3
/usr/bin/time java $opt  edu.rutgers.axs.indexer.ArxivImporter all $1 $2 $3


