#!/bin/csh

#-- Parses usage log files and processes/outputs specific events
#-- (refer to edu.rutgers.axs.ee4.HistoryClustering 
#-- for details of what events are kept)
#-- 
#-- This script is derived from json-blei.sh

if ("$1" == "") then
    echo 'Using current year'
    set year=`date +%Y`
else
    set year=$1
    echo "Setting year to $year"
endif
#
set opt="-Xmx4096m -DOSMOT_CONFIG=."

set home=$HOME
#set home=~vm293

set lib=$home/arxiv/lib
set tclib=/usr/local/tomcat/lib

set cp="$tclib/servlet-api.jar:$tclib/catalina.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"
set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:../lucene/lucene-3.3.0/contrib/benchmark/lib/commons-logging-1.0.4.jar"
set cp="${cp}:/usr/local/tomcat/bin/tomcat-juli.jar"

set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

# set opt="-cp ${cp} ${opt}"
set opt="-cp ${cp} ${opt} -Dtc=/data/json/usage/tc1.json.gz"

#echo "opt=$opt"

#set outdir=/data/arxiv/blei/usage1
set outdir="/tmp/arxiv_data"
 
foreach d (/data/json/usage) 
    #echo "Directory $d"

    if (! -e $d/$year) then
        echo "Directory $d/$year does not exist. Creating."
        mkdir $d/$year
    endif

    set files=`(cd $d; ls $year/??????_usage.json.gz)` 
    #set today=`date +%y%m%d`
    #set files=`(cd $d; ls $year/$today.json.gz)` 
    #echo 'files:' $files 

    foreach g ($files)
        set f="$d/$g"
        set g0=`echo $g|perl -pe 's/\.gz$// ; s/\.json$//'`

        set h="$outdir/${g0}_referrer-new.dat"

        set hdir=`dirname $h`
        if (! -e $hdir) then 
            echo "Directory $hdir does not exist. Creating."
            mkdir -p $hdir
        endif


        if (! -e $h.gz) then
            date
            echo "Converting file $f to $h"
            time java $opt edu.rutgers.axs.ee4.HistoryClustering bleiExtended $f $h

            gzip $h
        else
            echo "Skipping (already converted):", $h 
        endif

    end
end
