#!/usr/bin/perl

use strict;

#----------------------------------------------------------------------
# Shows the top terms for a topic. 
# Sample usage:
#  ~/arxiv/arxiv/util/show-topic.pl ldafit.topics 169
#----------------------------------------------------------------------

my $vocabFile = "/data/arxiv/ctpf/ldainit/vocab.dat";

my ($topicFile, $t) = @ARGV;

(defined $t) or die "Usage: $0 topicFile topicNo\n";

open( F , "$vocabFile") or die "Cannot read vocabulary file $vocabFile\n";
my @voc = <F>;
@voc = map { s/\s+$//; $_; } @voc;
close(F);

open( F , "$topicFile") or die "Cannot read topic file $topicFile\n";
my @allTopics = <F>;
close(F);

my $topicCnt = scalar(@allTopics);

(1 <= $t &&  $t <= $topicCnt) or die "Requested topic index $t is out of range; must be in [1:$topicCnt]\n";

my $topicLine = $allTopics[$t-1];
$topicLine =~ s/\s+$//;

print "Topic $t out of 1..$topicCnt\n";
# print "$topicLine\n";

my @cnts = split(/\s+/, $topicLine);

# ($#voc == $#cnts) or die "Vocabulary size mismatch: $#voc != $#cnts\n";

my @pairs = map { [$voc[$_], $cnts[$_]] } (0..$#voc);

@pairs = sort { $b->[1] <=> $a->[1] } @pairs;

for(my $i=0; $i<=$#pairs && $i<40; $i++) {
    my ($word, $cnt) = @{$pairs[$i]};
    print "$cnt $word\n";
}
