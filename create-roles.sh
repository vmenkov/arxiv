#!/bin/csh


set opt="-DOSMOT_CONFIG=$home/arxiv/arxiv"

set lib=$home/arxiv/lib

# lucene-core-1.9.1.jar

#-- catalina.jar and tomcat-juli.jar are needed for the RealmBase
#-- class. If your tomcat lives elsewhere, adjust paths accordingly

set tomcat=/usr/share/tomcat7

set cp="$tomcat/bin/tomcat-juli.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"

foreach x ($tomcat/lib/*.jar) 
    set cp="${cp}:$x"
end

set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

set opt="-cp ${cp} ${opt}"

#echo "opt=$opt"

java $opt edu.rutgers.axs.sql.CreateRoles


