#!/usr/bin/perl

#--------------------------------------------------- 
# This script extracts a sample out of a set. First M lines
# are skipped, and after that 1 line out of each N, starting from
# the 1st one, are extracted

use strict;

sub usage() {
    my ($msg) = @_;
    print "Usage: $0 input.csv M N out.csv\n";
    print "M=offset : number of lines to skip at the top of the file\n";
    print "N : print one line out of each N lines\n";
    if (defined $msg) { print "$msg\n"; }
    die;
}

if ($#ARGV != 3) {
    &usage();
    
}

my ($in, $offset, $oneOutOf, $out1) = @ARGV;

($offset >= 0) or die "M must be >= 0";
($oneOutOf >= 1) or die "M must be >= 1";

open(F, "<$in") or die "Cannot read $in\n";
open(G1, ">$out1") or die "Cannot write $in\n";

my $s=undef;
my $needToSkip = $offset;
while(defined ($s=<F>)) {
    if ($needToSkip == 0) {
	print G1 $s;
	$needToSkip =  $oneOutOf-1;
    } else {
	$needToSkip--;
    }
}


close(F);
close(G1);
