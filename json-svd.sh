#!/bin/csh

#-- Usage: emailSug.sh username email  [new_password]

set baseopt="-Xmx4096m -XX:MaxPermSize=256m -DOSMOT_CONFIG=."

set lib=$home/arxiv/lib
set tclib=/usr/local/tomcat/lib

set cp="$tclib/servlet-api.jar:$tclib/catalina.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"
set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:../lucene/lucene-3.3.0/contrib/benchmark/lib/commons-logging-1.0.4.jar"
set cp="${cp}:/usr/local/tomcat/bin/tomcat-juli.jar"

# on cactuar
set cp="${cp}:../tomcat-lib-to-copy/catalina.jar"

set jmlib=$home/arxiv/javamail-1.4.5/lib
foreach x ($jmlib/*.jar) 
    set cp="${cp}:${x}"
end

set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

set baseopt="-cp ${cp} ${baseopt}"

set opt="$baseopt"

echo "opt=$opt"

#-- List of categories to process. This command simply lists all subdirectories
#-- in ../arXiv-data/tmp/hc , so that all major cats are processed. For
#-- testing purposes, you can have a shorter list instead
set cats=`(cd ../arXiv-data/tmp/hc; /bin/ls)`
date

#-- Specify various options:
# -DusageFrom=20100101 -DusageTo=20130101   : the range of usage files
# -DarticleDateFrom=20100101 -DarticleDateTo=20120101 : article submission dates
# -Dk_svd=5 : the number of singular vectors to keep
# -Dk_kmeans=0 : the number of clusters to create. (0 means setting the number adaptively, based on the set size)
set opt="$baseopt -DusageFrom=20100101 -DusageTo=20130101 -DarticleDateFrom=20100101 -DarticleDateTo=20120101 -Dk_svd=5 -Dk_kmeans=0"


foreach cat ($cats) 
 echo Processing category $cat

 # One can add an option such as 
 # -DasgPath=../arXiv-data/tmp/svd-asg/$cat/asg.dat 
 # to control the location of the output file (the assignment map)

 time java $opt  edu.rutgers.axs.ee4.HistoryClustering svd $cat >& svd-${cat}.log
end


