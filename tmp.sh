#!/bin/csh


#foreach f ( `find /usr/lib/jvm/java-8-oracle -name '*.jar' ` )
foreach f ( `find /usr/share/tomcat6/webapps/arxiv/WEB-INF/lib/ -name '*.jar' ` )

     dir $f
     jar tf $f |grep persist
end

# Here
# /usr/share/tomcat6/webapps/arxiv/WEB-INF/lib/openjpa-2.1.1.jar
