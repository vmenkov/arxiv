#!/usr/bin/perl -s

use strict;

#------------------------------------------------------------------------
# An auxiliary scripts for LDA expriments. It is used to take a look
# at the file ldafit-test.doc.states produced in an LDA run, and to
# identify, for each document, the topic with the largest count. (So
# this is presumably the topic with which the document has the highest
# affinity).
#------------------------------------------------------------------------

my $m = (defined $::n? $::n : 1);

my $ia=0;
($ia <= $#ARGV) or die "Usage: $0 [-n=1] dirname\n";

my $dir = $ARGV[$ia++];
my $f="$dir/ldafit-test.doc.states";

open(F, "<$f") or die "Cannot read file $f";
my @lines = <F>;
close(F);

my $pos = 0;
my $n = scalar @lines;
foreach my $line (@lines) {
    $pos++;
    my @cnts = split(/\s+/, $line);
    my $sum = 0;
    foreach my $x (@cnts) { $sum += $x; }
    my @pairs = map { [$_+1, $cnts[$_]] } (0..$#cnts);
    @pairs = sort { $b->[1] <=> $a->[1] } @pairs;
    my ($t,$w) = @{$pairs[0]};
    print "Document $pos/$n: top topic". ($m>1? "s" : "") ." $t (cnt=$w/$sum)";
    if ($m>1) {
	#-- print "; other topics";
	for(my $k=1; $k<$m; $k++) {
	    ($t,$w) = @{$pairs[$k]};
	    if ($w==0) { last; }
	    print ", $t ($w)";
 	}
    }
    print "\n";

    print `../lda/show-topic.pl $dir/ldafit-test.topics $t`;

}

