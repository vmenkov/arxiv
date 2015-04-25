#!/bin/csh

#---------------------------------------------------------------------------
# This script runs the daily (nightly) recommendation list generator
# with a variety of options, the results being saved into separate
# files in a specified directory.
#
# It is similar to sb-ctpf-t.sh, sb-ctpf-d.sh, which provide a similar
# test harness for the session-based  recommendation list generator.

# Usage examples:
# nohup ./simulate-daily.sh -dir ../runs/sb3 sb.in-tj.dat > & tmp.log &
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
#set opt="${opt} -Dbasedir=/data/arxiv/ee5/20141201 -Dmode2014=true"

echo "opt=$opt"

if ("$1" == "") then
    echo 'Usage: $0 input-file-name [output-file-name]'
    exit
endif

set in=$1
set xin=`basename $1`

cat $in | ./cmd.sh showtitle - | grep -v docno= > $dir/${xin}.titles

foreach program (EE5) # (SUBJECTS ABSTRACTS COACCESS)
    set out="${xin}.${program}.out"
    set outTitles="${xin}.${program}.titles"

    date
    echo "program=$program"
    echo "Input=$in, output=$out, output+titles=$outTitles"

    /usr/bin/time java $opt -DshowScores=true -DshowTitles=true -Dprogram=$program   \
    edu.rutgers.axs.harness.TestHarness $in $dir/$out

    #-- extract the last list of AIDs, and look up article titles
#    perl -e \
#    '$_=join("",<>); s/.*Rec/Rec/sm; s/\t.*//g;  s/^[A-Z].*\n?//mg; print;' \
#    $dir/$out |  ./cmd.sh showtitle - |grep -v docno=  > $dir/$outTitles

end

