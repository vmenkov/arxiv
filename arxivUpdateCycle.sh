#!/bin/csh

#-----------------------------------------------------------------------------
#-- This script is run daily from the crontab. It runs the following
#-- 3 programs, sequentially:
#-- * ArxivImporter, which pulls new articles from arxiv.org;
#-- * ArticleAnalyzer, which updates article stats in the database
#-- * TaskMaster, which will keep running for the next 24 hours, serving
#---  suggestion list generation requests.
#-----------------------------------------------------------------------------

set d=`/bin/date +'%Y-%m-%d'`

echo "Today's log names will end in $d.log"

set baseopt="-DOSMOT_CONFIG=."

set lib=$home/arxiv/lib

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"

#set opt="-cp ${cp} ${opt} -Drewrite=false"
set baseopt="-cp ${cp} ${baseopt}"

set opt="${baseopt} -Drewrite=true -Ddays=3"
# -Dfrom=2012-01-16

echo "Options for ArxivImporter: $opt"

time /usr/bin/time java $opt edu.rutgers.axs.indexer.ArxivImporter all >& importer-${d}.log

set opt="${baseopt}"
echo "Options for ArticleAnalyzer: $opt"

time java $opt $1 $2 $3 edu.rutgers.axs.recommender.ArticleAnalyzer >& allnorms-${d}.log 

set  opt="-Xmx1024m ${baseopt} -DexitAfter=1"

time java $opt $1 $2 $3 edu.rutgers.axs.recommender.TaskMaster >& taskmaster-${d}.log





