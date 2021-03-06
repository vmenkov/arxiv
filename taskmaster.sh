#!/bin/csh

# Commands supported: 
# ... list
# ... show id
# ... showcoef id
# ....

# set opt="-DOSMOT_CONFIG=."
set opt="-Xmx2048m -DOSMOT_CONFIG=."

set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"

# set cp="${cp}:/usr/local/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

# -Dscheduler=60s

set opt="-cp ${cp} -Xmx1024m ${opt} -DexitAfter=20 -DarticlesUpdated=true"

echo "opt=$opt"


time java $opt $1 $2 $3 edu.rutgers.axs.recommender.TaskMaster



