#!/bin/csh

#-- Usage: emailSug.sh username email  [new_password]

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
set cp="${cp}:../tomcat-lib-to-copy/catalina.jar"

set cp="${cp}:$home/apache-openjpa-2.1.1/openjpa-all-2.1.1.jar"

# set opt="-cp ${cp} ${opt}"
set opt="-cp ${cp} ${opt}"

echo "opt=$opt"


set logbase=../runs/svm-halves-normalize-c100
#set logbase=../runs/svm-halves-idf-normalize
mkdir $logbase

set cats=`(cd ../arXiv-data/tmp/hc; /bin/ls)`
#set cats=(math)
date

foreach cat ($cats) 
# time java $opt  edu.rutgers.axs.ee4.HistoryClustering svd $cat >& svd-${cat}.log

#  if ($status) then
#    echo "Error while exporting data"
#    exit 1
#  endif


 set d=$arxiv/arXiv-data/tmp/svd-asg/$cat
 set logs=$logbase/$cat

 echo "Processing category $cat; data in $d, logs in $logs"

 if (! -e $logs) then
    mkdir $logs
 endif

 cp $0 $logs

    #-- split doc list
 ./split-set.pl $d/asg.dat 0.50 $d/asg-part1.dat $d/asg-part2.dat

set zopt="$opt"
set zopt="$zopt -Dnormalize=true"
#set zopt="$zopt -Didf=true"
set log=$logs/svm-prepare-${cat}.log
   #-- convert each part into an SVM input file
 time java $zopt -DasgPath=$d/asg-part1.dat edu.rutgers.axs.ee4.HistoryClustering svm $cat >& $log
 mv $d/exported.asg $d/exported-part1.asg
 mv $d/train.dat $d/train-part1.dat

 time java $zopt -DasgPath=$d/asg-part2.dat -DdicFile=$d/asg.dic edu.rutgers.axs.ee4.HistoryClustering svm $cat >>& $log
 mv $d/exported.asg $d/exported-part2.asg
 mv $d/train.dat $d/train-part2.dat

 ./cluster-sizes.pl $d/asg.dat > $logs/clusters.log 

   #-- train model on section 1
 set model=$d/model-part1.dat
 set log=$logs/svm-${cat}-learn.log
 set dat=$d/train-part1.dat
 $svm/svm_multiclass_learn -c 100 -f 2 $dat $model >& $log

 set failed=0
 if ($status) then
    echo "Error while training SVM on $dat"
    set failed=1
endif

set g=`grep -c "Out of memory" $log`
if ("$g" == "1") then
    echo "Out of memory error while training SVM on $dat"
    set failed=1
endif

if (! $failed) then
 set log=$logs/svm-${cat}-classify-halves.log
 echo "Testing on both halvs of the split set" > $log
  foreach z (1 2) 
   set dat=$d/train-part${z}.dat
   echo "Applying the model to the data set $dat" >> $log
   $svm/svm_multiclass_classify  $dat $model $d/results-train-part${z}.out  >>& $log
   ./cmp-asg.pl $d/exported-part${z}.asg $d/results-train-part${z}.out >> & $log
  end
endif
end

#------------------------------------------



