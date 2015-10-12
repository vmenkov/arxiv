#!/bin/bash

#---------------------------------------------------------------------
# Runs LDA followed by CTPF. 
# CTPF version: https://github.com/premgopalan/collabtm
# LDA version: The one currently on the above repository requires a small patch.
#---------------------------------------------------------------------
# This is part of the Laurent's CTPF initialization pipeline. It needs
# to be preceded by data preparation step: articles (abstracts only)
# exported from Lucene; usage data for these articles extracted from
# arxiv.org usage logs and converted to simple TSV format.
#---------------------------------------------------------------------

## USER TUNABLE PARAMETERS
NUM_TOPICS=5
DATA_DIR=~/arxiv/dat/dataset_2012_50K/ # Should contain clicks (train.tsv,validation.tsv,test.tsv)
DATA_WORDS=${DATA_DIR}/mult.dat     # Document content 

#CTPF_EXEC_DIR=/home/statler/lcharlin/arxiv/src/collabtm_stock/collabtm/src/ # location of collabtm executable
#LDA_EXEC_DIR=/home/statler/lcharlin/arxiv/src/collabtm/lda/lda-code/ # location of lda executable

# location of collabtm executable
CTPF_EXEC_DIR=~/arxiv/lda/collabtm-master/src/

# location of lda executable
LDA_EXEC_DIR=~/arxiv/lda/collabtm-master/lda/lda-code/

NUM_WORDS=14000 # Number of words in the vocabulary
NUM_DOCS=50000  # Number of documents
NUM_USERS=50748 # Number of users in the click data



##--------------- RUN LDA --------------------------------
# (cd $DATA_DIR;  ln -sf mult.dat mult_lda.dat)

LDA_FIT_DIR=lda_fit_${NUM_TOPICS}

#if [ ! -d $LDA_FIT_DIR ]; then 
#  echo 'Creating LDA fit dir' $LDA_FIT_DIR
#  mkdir $LDA_FIT_DIR
#else 
#  rm -f $LDA_FIT_DIR/*
#fi 

alpha=0.1

${LDA_EXEC_DIR}/lda --directory $LDA_FIT_DIR --train_data $DATA_WORDS --num_topics $NUM_TOPICS \
  --eta 0.01 --alpha $alpha --max_iter -1 --max_time -1 --detect_conv 1e-4

#echo Done for now
#exit

## COPY LDA OUTPUT TO a new directory under $DATA_DIR
CTPF_LDA_DIR=${DATA_DIR}/lda-fits
if [ ! -d $CTPF_LDA_DIR ]; then 
  echo 'Creating' $CTPF_LDA_DIR
  mkdir $CTPF_LDA_DIR
fi 

cp $LDA_FIT_DIR/final.topics     $CTPF_LDA_DIR/beta-lda-k${NUM_TOPICS}.tsv
cp $LDA_FIT_DIR/final.doc.states $CTPF_LDA_DIR/theta-lda-k${NUM_TOPICS}.tsv


## RUN CTPF 
CTPF_FIT_DIR=ctpf_fit

if [ ! -d $CTPF_FIT_DIR ]; then 
  echo 'Creating CTPF fit dir:' $CTPF_FIT_DIR
  mkdir $CTPF_FIT_DIR
else 
  rm -f $CTPF_FIT_DIR/*
fi 

OWD=`pwd`
cd $CTPF_FIT_DIR

${CTPF_EXEC_DIR}/collabtm -dir $DATA_DIR -nusers $NUM_USERS -ndocs $NUM_DOCS -nvocab $NUM_WORDS -k $NUM_TOPICS \
  -lda-init -fixeda -fixed-doc-param

cd $OWD
