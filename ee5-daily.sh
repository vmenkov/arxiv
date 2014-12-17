#!/bin/csh

#-- Usage examples:
# On SQL server, run "delete from EE5DocClass;"
# ee5-daily.sh init
# ee5-daily.sh update
#
#-- This script can (and often should) to be run under a different user, e.g:
# sudo -u tomcat6  ./ee5-daily.sh -home /home/vmenkov update


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

set opt="-cp ${cp} ${opt}"

echo "opt=$opt"

# /usr/bin/time  java $opt edu.rutgers.axs.ee5.Daily init
/usr/bin/time  java $opt edu.rutgers.axs.ee5.Daily $1





