#!/bin/csh

#-- This script runs ViewSuggestions, displaying the current
#-- recommendation list for a particular user.
#-- Usage:
#
#   viewSug.sh user_name

set opt="-Xmx2048m -DOSMOT_CONFIG=."

set lib=$home/arxiv/lib

set cp="/usr/local/tomcat/lib/servlet-api.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"
set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

#-- usual options for testing
set opt="-cp ${cp} ${opt}"

#-- options for a more "production" run, when a PresentedList object is recorded
# set opt="-cp ${cp} ${opt} -DdryRun=false"

echo "opt=$opt"

time java $opt  edu.rutgers.axs.web.ViewSuggestions $1



