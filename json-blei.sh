#!/bin/csh

#-- Converts JSON files as per David Blei's request (2013-10-11)

set opt="-Xmx4096m -DOSMOT_CONFIG=."

set lib=$home/arxiv/lib
set tclib=/usr/local/tomcat/lib

set cp="$tclib/servlet-api.jar:$tclib/catalina.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"
set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:../lucene/lucene-3.3.0/contrib/benchmark/lib/commons-logging-1.0.4.jar"
set cp="${cp}:/usr/local/tomcat/bin/tomcat-juli.jar"

# on cactuar
set cp="${cp}:../tomcat-lib-to-copy/catalina.jar"

set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

# set opt="-cp ${cp} ${opt}"
set opt="-cp ${cp} ${opt}"

echo "opt=$opt"

set outdir=/data/arxiv/blei/usage


#foreach f (~/arxiv/json/user_data/11030?_user_data.json) 
#foreach f (/data/json/usage/201[012]/*.json.gz) 

foreach d (/data/json/usage) 
echo "Directory $d"
set files=`(cd $d; ls 2010/100101*.json.gz)` 

foreach g ($files)
    set f="$d/$g"
    set g0=`echo $g|perl -pe 's/\.gz$//'`
    set h="$outdir/$g0"
    date
    echo "Converting file $f to $h"
echo     time java $opt edu.rutgers.axs.ee4.HistoryClustering blei $f $h

    if (-e $h.gz) then
      echo Deleting old file $h.gz
      rm $h.gz
    endif

    gzip $h
end
end

#time java $opt  edu.rutgers.axs.ee4.HistoryClustering svd physics



