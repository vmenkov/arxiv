#!/bin/csh

#----------------------------------------------------------------
#-- Usage examples:
#-- After a new clustering scheme is installed:
# ee5-daily.sh delete
# ee5-daily.sh init
#-- Nightly thereafter:
# ee5-daily.sh update
#
#-- This script can (and often should, to avoid messing with file permissions) 
#-- be run under a different user name, e.g:
# sudo -u tomcat6  ./ee5-daily.sh -home /home/vmenkov update
# sudo -u tomcat   ./ee5-daily.sh -home /home/vm293   init
#----------------------------------------------------------------

#-- Set the home directory as per the "-home" option. This is useful
#-- if run as a different user.
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

set opt="-cp ${cp} ${opt} -Dbasedir=/data/arxiv/ee5/20150415 -Dmode2014=false"

echo "opt=$opt"

# /usr/bin/time  java $opt edu.rutgers.axs.ee5.Daily init
/usr/bin/time  java $opt edu.rutgers.axs.ee5.Daily $1





