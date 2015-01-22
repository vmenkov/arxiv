#!/bin/csh

# Usage examples:
# ./import.sh files 'http://export.arxiv.org/oai2?verb=GetRecord&metadataPrefix=arXiv&identifier=oai:arXiv.org:1211.0003'


#-- Set the home directory as per the "-home" option. This is useful
#-- if run as a different user.
if ("$1" == "-home") then
    shift
    set home=$1
    shift
    echo "Setting home to $home"
endif

if ("$1" == "-days") then
    shift
    set days=$1
    shift
else 
    days=7
endif

echo "Set days=$days"


set opt="-DOSMOT_CONFIG=."

set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"


#set opt="-cp ${cp} ${opt} -Drewrite=false"
set opt="-cp ${cp} ${opt} -Drewrite=false -Doptimize=false -Ddays=${days}"
# -Dfrom=2012-01-16

echo "opt=$opt"

if ("$1" == "") then
    echo 'Usage: import.sh [all|files ...]'
    exit
endif

/usr/bin/time  java $opt edu.rutgers.axs.indexer.ArxivImporter $1 "$2" "$3" "$4"



