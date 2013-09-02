#!/bin/csh

#------------------------------------------------------------------------
# This script runs category search or general query search, taking the
# category list or the query form the standard input

# Examples:

# Category search
#   echo 'cs.*' | ./searchStdin.sh -Dcat=true -Ddays=200 -Dout=tmp.csv
#   echo cs.AI | ./searchStdin.sh -Dcat=true -Ddays=200 -Dout=tmp.csv
# no time restriction:
#   echo cs.AI | ./searchStdin.sh -Dcat=true -Ddays=0 -Dmaxlen=1000000 -Dout=tmp.csv
# 
# General query search
#  echo rabbit | ./searchStdin.sh -Dcat=false -Ddays=1200 -Dout=tmp.csv

# For query syntax examples, see 
# http://my.arxiv.org/arxiv/search_help.jsp
#------------------------------------------------------------------------
set opt="-DOSMOT_CONFIG=$home/arxiv/arxiv"

set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"

set opt="-cp ${cp} ${opt} -Dcat=true -Ddays=180 -Dcustom=true"

echo "opt=$opt"

# -Dout=tmp.csv

/usr/bin/time java $opt $argv edu.rutgers.axs.web.SearchResults -


