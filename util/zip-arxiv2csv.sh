#!/bin/csh

#--- Run this script in ~/arxiv, in order to package all the files needed
#--- to run ArxivToCsv on another machine without installing everything there

zip a2c.zip lib/xercesImpl.jar lib/xml-apis.jar \
arxiv/src/edu/rutgers/axs/ParseConfig.java arxiv/src/edu/rutgers/axs/indexer/ArxivToCsv.java arxiv/src/edu/rutgers/axs/indexer/XMLUtil.java arxiv/src/edu/rutgers/axs/indexer/XMLtoCSV.java arxiv/src/edu/rutgers/axs/indexer/XMLtoHash.java arxiv/src/edu/rutgers/axs/indexer/FieldHandler.java arxiv/src/edu/rutgers/axs/indexer/ArxivFields.java \
arxiv/src/edu/rutgers/axs/indexer/ArxivImporterBase.java \
arxiv/src/edu/rutgers/axs/indexer/AuthorsHandler.java \
arxiv/src/edu/rutgers/axs/util/OptionAccess.java \
arxiv/src/edu/rutgers/axs/sql/Logging.java \
arxiv/build-a2c.xml
