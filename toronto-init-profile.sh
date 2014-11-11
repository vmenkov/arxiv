#!/bin/csh

#-- This script initializes user profile for one user based on his
#-- uploaded docs

if ("$1" == "") then
    echo "Usage: $0 user_name [home_dir]"
    exit
endif

if ("$2" == "") then
else
    set home=$2
    echo "Setting home to $home"
endif

echo home=$home


set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

#-- looking for javax.persistence.Persistence
#-- ??

set stoplist=${home}/arxiv/arxiv/WEB-INF/stop200.txt 

set opt="-DOSMOT_CONFIG=."
set opt="-cp ${cp} ${opt} -Drefined=true  -Dstoplist=${stoplist}"

#echo PATH=$PATH
#which java
#setenv JAVA_HOME /usr/local/java/jdk1.6.0_30
#which java

echo "opt=$opt"

#echo /usr/bin/time ${JAVA_HOME}/bin/java  $opt -Duser=$1 edu.rutgers.axs.recommender.TorontoPPP init2

/usr/bin/time java  $opt -Duser=$1 edu.rutgers.axs.recommender.TorontoPPP init2






