#!/bin/csh

#-----------------------------------------------------------------------
# This script is used to export documents from My.ArXiv's Lucene data
# store as feature vectors using low-dimensional representation.
# This was written for Chen Bangrui (in Peter Frazier's team).
#
# For usage help, run this script with no arguments.
#
# To compute DF for all word clusters on the entire corpus (expensive)
#   low-dim-export.sh  df
# To export specifed docs
#   low-dim-export.sh aids id1 [id2 id3 ...]
# The same, with doc list on stdin
#   cat aid-list-file |  low-dim-export.sh  LowDimDocumentExporter aids -
# To export docs from a specified major category for 1 year
#  low-dim-export.sh cat [physics 2013]
#
# One can also do fine-grained date contol by modifying this script and calling
# the Java app differently:
# java [-Dfrom=2013-01-02 -Dto=2014-01-01] LowDimDocumentExporter cat [physics]
#
# For additional documentation, see class edu.rutgers.axs.ee5.LowDimDocumentExporter 
#-----------------------------------------------------------------------
#-- We set variable $home so that the script can find all necessary
#-- JAR files. The assumption is that the script lives in
#-- ~xxx/arxiv/arxiv (where xxx is the name of the user who has
#-- everything set up right), so that scriptdir/../.. is ~xxx.  If you
#-- copy this script to your own directory (e.g. to customize it), you
#-- may need to modify variable $home accodingly, e.g. by setting it
#-- to ~vm293.
#-----------------------------------------------------------------------

#-- the directory where this script lives
set scriptdir=`dirname $0`
set home=$scriptdir/../..
# echo "Using home=$home"


set opt="-DOSMOT_CONFIG=$home/arxiv/arxiv"
set lib=$home/arxiv/lib

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"
set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
#-- this is indeed needed for LowDimExporter 
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

set opt="-cp ${cp} ${opt}"

#echo "opt=$opt"

java $opt edu.rutgers.axs.ee5.LowDimDocumentExporter $argv


