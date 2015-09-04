#!/usr/bin/perl -s

use strict;

#--------------------------------------------------------------------
# This is an auxuiliary script used to analyze the values of thetas in
# a file produced by the LDA init or LDA update tool. Do these values
# look like they have been produced by Laurent's formula (the one based 
# on alpha and the sample counts?
#--------------------------------------------------------------------

my $alpha = (defined $::alpha? $::alpha : 0.25);

print "If alpha=$alpha...\n";

foreach my $s (<>) {
    my @q = split(/\s+/, $s);
    my $j = shift @q;
    shift @q;
    my ($n, $sumT, $minT) = (0, 0, $q[0]);
    foreach my $t (@q) {
	$sumT += $t;
	if ($t < $minT) { $minT = $t; }
	$n ++;
    }
    my $s2 = ($minT==0 ?  "n/a" : $alpha*(1.0/$minT - $n));
    print "j=$j;  sumTheta=$sumT;  minTheta= $minT = alpha/(alpha*$n+s) (guess s=$s2)\n";
}

