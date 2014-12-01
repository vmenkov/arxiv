#!/bin/csh

#---------------------------------------------------------------------
#-- This script is run daily from the crontab. It runs the following
#-- 3 programs, sequentially:
#-- * ArxivImporter, which pulls new articles from arxiv.org;
#-- * ArticleAnalyzer, which updates article stats in the database
#-- * TaskMaster, which will keep running for the next 24 hours, serving
#---  suggestion list generation requests.
#
# There is a provision to explicitly specify the value of $home (via
# the 1st command-line argument), which is used as a base for finding
# jar files etc. This is for the convenience of running the script as
# a different user (e.g. with crontab)
#----------------------------------------------------------------------

/bin/date

set d=`/bin/date +'%Y-%m-%d'`

echo "Today's log names will end in $d.log"

echo home=$home

echo arg0=$0


if ("$1" == "") then
else
    set home=$1
    echo "Setting home to $home"
endif


#-- OSMOT_CONFIG is the directory where osmot.conf is

set baseopt="-DOSMOT_CONFIG=${home}/arxiv/arxiv"

set lib=$home/arxiv/lib

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"

#-- for applications that use MySQL (via OpenJPA)
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

#-- for the EmailSug application, which needs Java Mail
set jmlib=$home/arxiv/javamail-1.4.5/lib
foreach x ($jmlib/*.jar) 
    set cp="${cp}:${x}"
end


#set opt="-cp ${cp} ${opt} -Drewrite=false"
set baseopt="-cp ${cp} ${baseopt}"

set opt="${baseopt} -Drewrite=true -Doptimize=true -Ddays=7"
# -Dfrom=2012-01-16

echo "Options for ArxivImporter: $opt"

time /usr/bin/time java $opt edu.rutgers.axs.indexer.ArxivImporter all >& importer-${d}.log

mv missing.txt missing-${d}.txt

set opt="-Xmx2048m ${baseopt}"
echo "Options for ArticleAnalyzer: $opt"

time java $opt  edu.rutgers.axs.recommender.ArticleAnalyzer >& allnorms-${d}.log 

set stoplist=${home}/arxiv/arxiv/WEB-INF/stop200.txt 

#-- Taking care of new articles for Exploration Engine ver. 3
time java $opt -Dstoplist=${stoplist}  edu.rutgers.axs.bernoulli.Bernoulli recent >& bernoulli-${d}.log 

#-- Updating class stats and recommendations for Exploration Engine ver. 4
time java $opt  -Dstoplist=${stoplist}   edu.rutgers.axs.ee4.Daily update >& ee4-${d}.log 

#-- Updating user profiles and recommendations for the 3PR (PPP) engine
time java $opt  -Dstoplist=${stoplist} edu.rutgers.axs.recommender.DailyPPP update >& ppp-${d}.log 

#-- send emails to subscribers
time java $opt -Dforce=false -Dsmtp=localhost edu.rutgers.axs.web.EmailSug  >& email-${d}.log 

set  opt="-Xmx1024m ${baseopt} -DexitAfter=22 -DarticlesUpdated=true"

time java $opt edu.rutgers.axs.recommender.TaskMaster >& taskmaster-${d}.log

