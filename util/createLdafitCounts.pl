#!/usr/bin/perl -s

use strict;

#--------------------------------------------------------------------
# This script creates ldafit.counts from ldafit.topics. Each 
# line of the former file contains a single number: the sum of values
# from the corresponding line of the latter file.
#
# wc ldafit.topics
#    100 1400000 2854473 ldafit.topics
#
# Usage
#   createLdafitCounts.pl ldafit.topic > ldafit.counts
#--------------------------------------------------------------------


foreach my $s (<>) {
    my @q = split(/\s+/, $s);
    my  $sum = 0;
    foreach my $t (@q) {
	$sum += $t;
    }
    print "$sum\n";
}

