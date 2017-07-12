set logscale xy
set grid xtics ytics
set grid nomxtics nomytics

set term x11 1
set title 'Score differences'
plot \
"coaccess-diff.dat" index "[Rank 1]"with lines title "Rank 1", \
"coaccess-diff.dat" index "[Rank 2]"with lines title "Rank 2", \
"coaccess-diff.dat" index "[Rank 3]"with lines title "Rank 3", \
"coaccess-diff.dat" index "[Rank 4]"with lines title "Rank 4", \
"coaccess-diff.dat" index "[Rank 5]"with lines title "Rank 5", \
"coaccess-diff.dat" index "[Rank 6]"with lines title "Rank 6", \
"coaccess-diff.dat" index "[Rank 7]"with lines title "Rank 7", \
"coaccess-diff.dat" index "[Rank 8]"with lines title "Rank 8", \
"coaccess-diff.dat" index "[Rank 9]"with lines title "Rank 9", \
"coaccess-diff.dat" index "[Rank 10]"with lines title "Rank 10"


set term png large
set out "coaccess-diff.png"
replot

set term x11 2
set title 'Scores'
plot \
"coaccess-count.dat" index "[Rank 1]"with lines title "Rank 1", \
"coaccess-count.dat" index "[Rank 2]"with lines title "Rank 2", \
"coaccess-count.dat" index "[Rank 3]"with lines title "Rank 3", \
"coaccess-count.dat" index "[Rank 4]"with lines title "Rank 4", \
"coaccess-count.dat" index "[Rank 5]"with lines title "Rank 5", \
"coaccess-count.dat" index "[Rank 6]"with lines title "Rank 6", \
"coaccess-count.dat" index "[Rank 7]"with lines title "Rank 7", \
"coaccess-count.dat" index "[Rank 8]"with lines title "Rank 8", \
"coaccess-count.dat" index "[Rank 9]"with lines title "Rank 9", \
"coaccess-count.dat" index "[Rank 10]"with lines title "Rank 10"


set term png large
set out "coaccess-count.png"
replot

