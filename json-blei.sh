#!/bin/csh

#-- Converts JSON files to space-separated files for use by David
#-- Blei's team, as per David Blei's request (2013-10-11)

if ("$1" == "") then
    echo usage "$0 yyyy"
    exit 1
else
    set year=$1
    echo "Setting year to $year"
endif

set opt="-Xmx4096m -DOSMOT_CONFIG=."

#-- home will resolve to ~vm293, regardless of who runs the script
set home=`dirname $0`/../..

set lib=$home/arxiv/lib
set tclib=/usr/local/tomcat/lib

set cp="$tclib/servlet-api.jar:$tclib/catalina.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"
set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:../lucene/lucene-3.3.0/contrib/benchmark/lib/commons-logging-1.0.4.jar"
set cp="${cp}:/usr/local/tomcat/bin/tomcat-juli.jar"

set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

# set opt="-cp ${cp} ${opt}"
set opt="-cp ${cp} ${opt} -Dtc=/data/json/usage/tc1.json.gz"

echo "opt=$opt"

set outdir=/data/arxiv/blei/usage1

 
#foreach f (~/arxiv/json/user_data/11030?_user_data.json) 
#foreach f (/data/json/usage/2003/*.json.gz) 

foreach d (/data/json/usage) 
echo "Directory $d"

if (-e $d/$year) then
else
    echo "Directory $d/$year does not exist!"
    exit 1
endif

set files=`(cd $d; ls $year/??????_usage.json.gz)` 

foreach g ($files)
    set f="$d/$g"
    set g0=`echo $g|perl -pe 's/\.gz$// ; s/\.json$//'`

    set h="$outdir/${g0}.dat"
    
    date
    echo "Converting file $f to $h"
    time java $opt edu.rutgers.axs.ee4.HistoryClustering blei $f $h

    if (-e $h.gz) then
      echo Deleting old file $h.gz
      rm $h.gz
    endif

    gzip $h
end
end

#time java $opt  edu.rutgers.axs.ee4.HistoryClustering svd physics



