#!/bin/csh

#-- Usage: emailSug.sh username email  [new_password]

# set opt="-DOSMOT_CONFIG=."
set opt="-Xmx4096m -DOSMOT_CONFIG=."

set lib=$home/arxiv/lib
set tclib=/usr/local/tomcat/lib

set cp="$tclib/servlet-api.jar:$tclib/catalina.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"
set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:../lucene/lucene-3.3.0/contrib/benchmark/lib/commons-logging-1.0.4.jar"
set cp="${cp}:/usr/local/tomcat/bin/tomcat-juli.jar"

# on cactuar
set cp="${cp}:../tomcat-lib-to-copy/catalina.jar"

set jmlib=$home/arxiv/javamail-1.4.5/lib

# set cp="${cp}:$jmlib/mailapi.jar"
foreach x ($jmlib/*.jar) 
    set cp="${cp}:${x}"
end

set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

# set opt="-cp ${cp} ${opt}"
set opt="-cp ${cp} ${opt}"

echo "opt=$opt"


foreach f (/home/vmenkov/arxiv/json/user_data_2/1?????_usage.json) 
# foreach f (/data/json/usage/201[012]/*.json.gz) 
    date
    echo Data from file $f
    time java $opt edu.rutgers.axs.util.CountArxivUsers $f
end




