#!/bin/csh

#---------------------------------------------------------------------------
# This script runs the CTPF fit data updater
#
# Usage examples:
# nohup ./update-fit.sh -dir ~/arxiv/runs/test
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

set baseopt="-cp ${cp} -DOSMOT_CONFIG=${home}/arxiv/arxiv"


if (! -e $dir) then
    echo "Failed to create directory $dir. Make sure the parent directory is writeable, and try again!"
    exit
endif



set frac=0.001

#echo "Exporting  fraction=$frac of new documents"

#set opt="${baseopt} -Dout=$dir/mult.dat -DitemsOut=$dir/new-items.tsv"
#echo "opt=$opt"

#/usr/bin/time java $opt -Dfraction=0.001 edu.rutgers.axs.ctpf.CTPFUpdateFit export new 

#echo "Done exporting"

#-- need this on en-myarxiv to run LDA: 
# setenv LD_LIBRARY_PATH /usr/local/lib

set model=ldafit
set topics=250
set subdir=test_${topics}

#echo "Sym-linking $model files, and runing lda in $dir"
#(cd $dir; \
#ln -s  /data/arxiv/ctpf/ldainit/${model}.* . ; \
#lda --test_data mult.dat --num_topics $topics --directory $subdir/ --model_prefix $model > & lda.log )

#echo "Done LDA"

set opt="${baseopt} -Dtopics=250 -DitemsNew=$dir/new-items.tsv -Dstates=$dir/$subdir/ldafit-test.doc.states -DoutDir=/data/arxiv/ctpf/lda.update"

echo "Runing post.lda with opt=$opt"

/usr/bin/time java $opt edu.rutgers.axs.ctpf.CTPFUpdateFit post.lda 
