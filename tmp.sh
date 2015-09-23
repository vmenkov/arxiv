#!/bin/csh

#------------------------------------------------------------------------
# This script pulls metadata from the ArXiv OAI2 server, and saves them into
# a CSV file, for processing by Laurent's code. Articles are selected by
# timestamp.
#------------------------------------------------------------------------
# Usage example:
# import-csv.sh 2013 details-2013.csv
#------------------------------------------------------------------------

#-- the directory where this script lives
set scriptdir=`dirname $0`

#-- the assumption is that the script lives in ~xxx/arxiv/arxiv (where
#-- xxx is the name of the user who has everything set up right), so
#-- that scriptdir/../.. is ~xxx. 

set home=$scriptdir/../..

echo "Using home=$home"

set opt="-DOSMOT_CONFIG=."

set lib=$home/arxiv/lib

#-- Not needed these Jar files in the classpath
# $lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar
# $lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:

set cp="$lib/axs.jar:$lib/lucene-core-3.3.0.jar"
set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"

# set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

set opt="-cp ${cp} ${opt}"
# -Dfrom=2012-01-16
#set opt="${opt} -Ddays=1"

# echo "opt=$opt"

if ("$1" == "") then
    echo 'Usage: import-csv.sh year [outputfile]'
    exit 1
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

#/usr/bin/time java $opt -Dout=$out -Dfrom=$from -Duntil=$until edu.rutgers.axs.indexer.ArxivToCsv csv

/usr/bin/time java $opt -Dout=$out -Ddays=2 edu.rutgers.axs.indexer.ArxivToCsv csv

if ($? != 0) then
    echo "The data importer (ArxivToCsv) apparently failed"
    exit 1 
endif
