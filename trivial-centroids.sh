#!/bin/csh

#-- Usage examples:
#
#-- This script can (and often should) to be run under a different user, e.g:

# ./trivial-centroids.sh -home /home/vmenkov initsplit ../ee5/tmp
# ./trivial-centroids.sh -home /home/vmenkov testsplit
# sudo -u tomcat6  ./trivial-centroids.sh -home /home/vmenkov init ../ee5/tmp
# sudo -u tomcat6  ./trivial-centroids.sh -home /home/vmenkov test 


#-- Set the home directory as per the "-home" option
if ("$1" == "-home") then
    shift
    set home=$1
    shift
    echo "Setting home to $home"
endif

set opt="-DOSMOT_CONFIG=${home}/arxiv/arxiv"

set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

set opt="-cp ${cp} ${opt} -Dyears=3"

echo "opt=$opt"

/usr/bin/time  java $opt edu.rutgers.axs.ee5.TrivialCentroids $1 $2






