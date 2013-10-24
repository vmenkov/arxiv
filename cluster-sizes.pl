#!/usr/bin/perl

#-------------------------------------------------------------------------
# This script is used to report on cluster sizes in a cluster map file
#-------------------------------------------------------------------------

use strict;

my ($fasg) = @ARGV;

open(F, "<$fasg") or die "Can't read $fasg";
my @list1 = map {s/.*,(\d+)\s*/$1/; $_;}   <F>;
close(F);

my @cnt = ();

foreach my $q (@list1) {
    $cnt[$q-1] ++;
}

my $sum = 0;
my $maxCnt = 0;
print "Cluster sizes: " . join(" ", @cnt) . "\n";
foreach my $q (@cnt) {
    $sum += $q;
    if ($q > $maxCnt) { $maxCnt = $q; }
}

my $maxRatio = $maxCnt / $sum;

printf( "Largest cluster: $maxCnt / $sum =  %5.2f%%\n",$maxRatio );
