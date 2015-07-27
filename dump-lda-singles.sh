#!/bin/csh

#---------------------------------------------------------------------------
#---------------------------------------------------------------------------

#-- Set the home directory as per the "-home" option. This is useful
#-- if run as a different user.
if ("$1" == "-home") then
    shift
    set home=$1
    shift
    echo "Setting home to $home"
endif


if ("$1" == "-dir") then
    shift
    set dir=$1
    shift
else 
    set dir="."
endif

echo "Output directory for all runs is $dir"



set opt="-DOSMOT_CONFIG=${home}/arxiv/arxiv"

set lib=$home/arxiv/lib

#-- trying different locations for Tomcat

set tomcat=/usr/share/tomcat7
set tomcat6=/usr/share/tomcat6

set cp="$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar:$lib/commons-math3-3.4.1.jar"

if (-e $tomcat) then
    set cp="${cp}:$tomcat/bin/tomcat-juli.jar"
    foreach x ($tomcat/lib/*.jar) 
	set cp="${cp}:$x"
    end
else if (-e $tomcat6) then
    set cp="${cp}:$tomcat6/bin/tomcat-juli.jar"
    foreach x ($tomcat6/lib/*.jar) 
	set cp="${cp}:$x"
    end
endif


set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"


set opt="-cp ${cp} ${opt}"

echo "opt=$opt"

#if ("$1" == "") then
#    echo 'Usage: $0 input-file-name [output-file-name]'
#    exit
#endif

#set in=$1
#set xin=`basename $1`

#set aid=$1
#if ($aid == "") then
#    echo "Must specify aid"
#endif

foreach aid ($argv)

    set xaid=`echo $aid| perl -pe 's/\//-/g'`
    echo "aid=$aid, escaped to $xaid"

    /usr/bin/time java $opt edu.rutgers.axs.ctpf.CTPFUpdateFit export aids $aid
    ls -lt | head -3



    mv mult.dat mult.aid=${xaid}.dat
    mv new-items.tsv items.aid=${xaid}.tsv



end

#-- need this on en-myarxiv: 
# setenv LD_LIBRARY_PATH /usr/local/lib

# nohup lda --test_data mult.dat --num_topics 250 --directory test_250/ --model_prefix ldafit > & lda.log &
