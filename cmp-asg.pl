#!/usr/bin/perl

#-------------------------------------------------------------------------
# This script is used to check the results of svm_multiclass_classify
# when applied to its own training set
# Usage:
#
# ~/arxiv/svm/svm_multiclass_learn -c 1 train.dat model.dat
#  ~/arxiv/svm/svm_multiclass_classify train.dat model.dat classify-out.dat
#  perl ~/arxiv/arxiv/cmp-asg.pl exported.asg classify-out.dat
#-------------------------------------------------------------------------

use strict;

my ($fasg, $ftest) = @ARGV;

open(F, "<$fasg") or die "Can't read $fasg";
my @list1 = map {s/.*,(\d+)\s*/$1/; $_;}   <F>;
close(F);

#print @list1;


open(G, "<$ftest") or die "Can't read $ftest";
my @list2 = map {s/(\d+) .*/$1/; $_;}   <G>;
close(G);

(scalar(@list1) == scalar(@list2)) or
die "Length mismatch: ".scalar(@list1). " vs " . scalar(@list2);

my $eqCnt=0;
my $neCnt=0;

foreach my $i (0..$#list1) {
    if ($list1[$i] == $list2[$i]) {
	    $eqCnt ++;
    } else {
	    $neCnt ++;
	}
}

printf( "Match: $eqCnt, misMath: $neCnt, correct percentage %5.2f%%\n",
	($eqCnt / ($eqCnt + $neCnt)) * 100 );
