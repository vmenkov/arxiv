#!/bin/csh

#---------------------------------------------------------------------------
# This script runs the SB recommendation list generator with a variety of options,
# the results being saved into separate files in a specified directory.
#
# Usage examples:
# nohup ./sb-ctpf-d.sh -dir ../runs/sb3 sb.in-tj.dat > & sb3.log &
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


set opt="-cp ${cp} ${opt} -DsbStableOrder=0"

echo "opt=$opt"

if ("$1" == "") then
    echo 'Usage: $0 input-file-name [output-file-name]'
    exit
endif

set in=$1
set xin=`basename $1`

cat $in | ./cmd.sh showtitle - | grep -v docno= > $dir/${xin}.titles

foreach sbMethod () # (SUBJECTS ABSTRACTS COACCESS)
    set out="${xin}.${sbMethod}.out"
    set outTitles="${xin}.${sbMethod}.titles"

    date
    echo "sbMethod=$sbMethod"
    echo "Input=$in, output=$out, output+titles=$outTitles"

    /usr/bin/time java $opt -DsbMergeWithBaseline=false  -DsbMethod=$sbMethod   \
    edu.rutgers.axs.sb.SBRGeneratorCmdLine $in $dir/$out

    #-- extract the last list of AIDs, and look up article titles
    perl -e \
    '$_=join("",<>); s/.*Rec/Rec/sm; s/\t.*//g;  s/^[A-Z].*\n?//mg; print;' \
    $dir/$out |  ./cmd.sh showtitle - |grep -v docno=  > $dir/$outTitles

end


set sbMethod="CTPF"

foreach t () # (Infinity 1 1e-1 1e-2 1e-3 1e-4 1e-5 1e-6 1e-7)
    set out="${xin}.${sbMethod}.t=${t}.out"
    set outTitles="${xin}.${sbMethod}.t=${t}.titles"

    date
    echo "sbMethod=$sbMethod; T=$t"
    echo "Input=$in, output=$out, output+titles=$outTitles"

    /usr/bin/time java $opt -DsbMergeWithBaseline=false -DsbMethod=$sbMethod -Dsb.CTPF.T=$t  \
    edu.rutgers.axs.sb.SBRGeneratorCmdLine $in $dir/$out

    #-- extract the last list of AIDs, and look up article titles
    perl -e \
    '$_=join("",<>); s/.*Rec/Rec/sm; s/\t.*//g;  s/^[A-Z].*\n?//mg; print;' \
    $dir/$out |  ./cmd.sh showtitle - |grep -v docno=  > $dir/$outTitles
    
end

foreach D (0 0.5 1.5 3) # ( 1 1.5)
    set out="${xin}.${sbMethod}.D=${D}.out"
    set outTitles="${xin}.${sbMethod}.D=${D}.titles"

    date
    echo "sbMethod=$sbMethod; D=$D"
    echo "Input=$in, output=$out, output+titles=$outTitles"

    /usr/bin/time java $opt -DsbMergeWithBaseline=false -DsbMethod=$sbMethod -Dsb.CTPF.D=$D  \
    edu.rutgers.axs.sb.SBRGeneratorCmdLine $in $dir/$out

    #-- extract the last list of AIDs, and look up article titles
    perl -e \
    '$_=join("",<>); s/.*Rec/Rec/sm; s/\t.*//g;  s/^[A-Z].*\n?//mg; print;' \
    $dir/$out |  ./cmd.sh showtitle - |grep -v docno=  > $dir/$outTitles
    
end

