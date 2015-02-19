#!/bin/csh

# Usage example:
# import-csv.sh 2013 details-2013.csv

#-- the directory where this script lives
set scriptdir=`dirname $0`

#-- the assumption is that the script lives in ~xxx/arxiv/arxiv (where
#-- xxx is the name of the user who has everything set up right), so
#-- that scriptdir/../.. is ~xxx. 

set home=$scriptdir/../..

echo "Using home=$home"

set opt="-DOSMOT_CONFIG=."

set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"


set opt="-cp ${cp} ${opt}"
#set opt="-cp ${cp} ${opt} -Ddays=7"
# -Dfrom=2012-01-16
set opt="${opt} -Ddays=1"

# echo "opt=$opt"

if ("$1" == "") then
    echo 'Usage: import-csv.sh year [outputfile]'
    exit
else
    set year=$1
endif

set from=${year}-01-01
set until=${year}-12-31

if ("$2" == "") then
    set out=details-${year}.csv
else
    set out=$2
endif

echo "Will request metadata for articles with time stamps from $from thru $until, and save them to file $out"

/usr/bin/time java $opt -Dout=$out -Dfrom=$from -Duntil=$until edu.rutgers.axs.indexer.ArxivToCsv csv



