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
    set dir="/data/arxiv/tmp/lda.inputs"
    if (-e $dir) then
        echo "Removing old directory $dir"
	rm -rf $dir
    endif
endif

echo "Output directory for article dump is $dir"

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


if (-e $dir) then
    echo "Directory $dir already exists. Please specify a different (non-existent) directory with the '-dir dirname' option, or delete this directory and let the script recreate it"
    exit
endif

echo "Creating run directory $dir"
mkdir $dir

if ($? != 0) then
    echo "Failed to create directory $dir (mkdir failed)"
    exit 
else if (! -e $dir ||  ! -d $dir) then
    echo "Failed to create directory $dir. Make sure the parent directory is writeable, and try again!"
    exit
endif



#if ("$1" == "") then
#    echo 'Usage: $0 input-file-name [output-file-name]'
#    exit
#endif

#set in=$1
#set xin=`basename $1`

set frac=0.001

echo "Exporting  fraction=$frac of new documents"

set opt="${baseopt} -Dout=$dir/mult.dat -DitemsOut=$dir/new-items.tsv"
echo "opt=$opt"

/usr/bin/time java $opt -Dfraction=0.001 edu.rutgers.axs.ctpf.CTPFUpdateFit export new 


if ($? != 0) then
    echo "Exporter apparently failed (exit code $?)"
    exit 
endif

echo "Done exporting"

#-- need this on en-myarxiv to run LDA: 
setenv LD_LIBRARY_PATH /usr/local/lib

set model=ldafit
set topics=250
set subdir=test_${topics}
set alpha=0.25
set ldainit=/data/arxiv/ctpf/ldainit/
set ldainit=nusers50748-ndocs50000-nvocab14000-k100-batch-bin-vb-fa-ldainit/

echo "Sym-linking $model files, and runing lda in $dir"
(cd $dir; \
ln -s  ${ldainit}/${model}.* . ; \
lda --test_data mult.dat --num_topics $topics --directory $subdir/ --model_prefix $model  --alpha $alpha > & lda.log )

if ($? != 0) then
    echo "LDA app apparently failed (exit code $?)"
    exit 
endif

echo "Done LDA"

set udirbase=/data/arxiv/ctpf/
set today=`date +%Y%m%d`
set udir=$udirbase/lda.update.$today

if (-e $udir) then
   rm $udir/*
   rmdir $udir
endif

mkdir $udir

if ($? != 0) then
    echo "Failed to create directory $udir (mkdir failed)"
    exit 
else if (! -e $udir || ! -d $udir) then
    echo "Failed to create directory $udir. Make sure the parent directory is writeable, and try again!"
    exit
endif


cp $dir/new-items.tsv $udir/

set opt="${baseopt} -Dtopics=${topics} -DitemsNew=$udir/new-items.tsv -Dstates=$dir/$subdir/ldafit-test.doc.states -DoutDir=$udir -Dalpha=${alpha} -Dldainit=${ldainit}"

echo "Runing post.lda with opt=$opt"

/usr/bin/time java $opt edu.rutgers.axs.ctpf.CTPFUpdateFit post.lda 

if ($? != 0) then
    echo "Post-LDA data processing apparently failed (exit code $?)"
    exit 
endif

echo "Done post-processing. New data files should be in $udir"

set dirlink=$udirbase/lda.update
if (-e $dirlink) then
    if (-d $dirlink) then 
	echo "$dirlink is an actual directory, not a symlink; please delete it! Not doing symlink"
	exit
    endif
    rm $dirlink
endif

if (-e $dirlink) then
    echo "There is already $dirlink, which can't be removed. Can't do symlinking"
endif

echo "Doing symbolic linking: ln -s $udir $dirlink"
ln -s $udir $dirlink

if ($? != 0) then
    echo "Symbolic linking (ln -s $udir $dirlink) apparently failed; code $?"
    exit 
endif

echo "Done linking to $dirlink"
echo "All done"
