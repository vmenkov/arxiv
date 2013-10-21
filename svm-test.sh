#!/bin/csh

#-------------------------------------------------------------------
# This script is used to apply SVM to a sample of pre-classified data.
# A large pre-existing cluster assignment map file is taken (e.g., one
# exported from the MySQL server); two small samples are extracted;
# the SVM model is trained on one sample, and then applied to the
# other sample. In this way, we can see to which extent the clustering
# described by the given cluster assignment map can be reified as 
# a partitioning in the feature space over which the SVM works.

#
# Usage:  ...
#-------------------------------------------------------------------

# set opt="-DOSMOT_CONFIG=."
set opt="-Xmx4096m -XX:MaxPermSize=256m -DOSMOT_CONFIG=."

set arxiv=$home/arxiv
set lib=$arxiv/lib
set tclib=/usr/local/tomcat/lib
set svm=$arxiv/svm

set cp="$tclib/servlet-api.jar:$tclib/catalina.jar:$lib/axs.jar:$lib/colt.jar:$lib/commons-fileupload-1.2.1.jar:$lib/commons-io-1.4.jar:$lib/lucene-core-3.3.0.jar:$lib/mysql-connector-java-3.1.12-bin.jar:$lib/nutch-0.7.jar"
set cp="${cp}:$lib/xercesImpl.jar:$lib/xml-apis.jar"
set cp="${cp}:../lucene/lucene-3.3.0/contrib/benchmark/lib/commons-logging-1.0.4.jar"
set cp="${cp}:/usr/local/tomcat/bin/tomcat-juli.jar"

# on cactuar
#set cp="${cp}:../tomcat-lib-to-copy/catalina.jar"

set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

set opt="-cp ${cp} ${opt}"
echo "opt=$opt"

set masterAsg=../classic-2012-asg.csv

grep '0$' $masterAsg | perl -pe 's/0$//' > ../classic-2012-asg-fewCats.csv
set masterAsg=../classic-2012-asg-fewCats.csv

set d=tmp
set logs=../runs/svm

if (! -e $logs) then
    mkdir $logs
endif

if (! -e $d) then
    mkdir $d
endif

echo "Using directory $logs for logs, $d for data files"

./sample-set.pl ../classic-2012-asg.csv 0 100 $d/sample1-asg.dat
./sample-set.pl ../classic-2012-asg.csv 1 100 $d/sample2-asg.dat

 #-- convert each part into an SVM input file
foreach x (1 2) 
 set zopt="$opt"
 echo  "x is $x"
 if ($x == 2) then
    set zopt="$opt -DdicFile=$d/asg.dic"
  endif
  echo zopt=$zopt
 time java $zopt  -DasgPath=$d/sample${x}-asg.dat -DsvmDir=$d edu.rutgers.axs.ee4.HistoryClustering svm >& $logs/svm-sample-prepare${x}.log
   echo Exported set $x

  if ($status) then
    echo "Error while exporting data"
    exit 1
  endif

 mv $d/exported.asg $d/exported-part${x}.asg
 mv $d/train.dat $d/train-part${x}.dat

end
echo Exported both sets 

#-- train model on section 1
set model=$d/model-part1.dat
set dat=$d/train-part1.dat
$svm/svm_multiclass_learn -c 10 -f 2 $dat $model >& $logs/svm-sample-learn.log
set log=$logs/svm-sample-classify-halves.log

if ($status) then
    echo "Error while training SVM on $dat"
    exit 1
endif


echo "Testing on both halves of the split set" > $log
foreach z (1 2) 
   set dat=$d/train-part${z}.dat
   echo "Applying the model to the data set $dat" >> $log
   echo "Applying the model to the data set $dat" 
   $svm/svm_multiclass_classify  $dat $model $d/results-train-part${z}.out  >>& $log

   if ($status) then
	echo "Error while classifying $dat with SVM"
	exit 1
   endif

   ./cmp-asg.pl $d/exported-part${z}.asg $d/results-train-part${z}.out >> & $log
    echo "done comparing for $dat"
end

echo "All done"




