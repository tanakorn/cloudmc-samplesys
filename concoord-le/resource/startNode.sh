#!/usr/bin/env bash
if [ $# -ne 2 ]; then
  echo "usage: startNode.sh <nodeId> <ipcDir>"
  exit 1
fi

nodeId=$(($1 + 14000))
ipcDir=$2
working_dir=WORKING_DIR
#PARENTPORT is going to be the parent node that will be joined by other. In default example 14000 using localhost

PARENTPORT="$nodeId"
if [ "$PARENTPORT" = "" ]
then
	echo "In order to use please do ./startNode.sh PARENTPORT e.g. ./start1node 14000"
else
	echo "Executing command :concoord replica -o concoord.object.bank.Bank -a 127.0.0.1 -p " $PARENTPORT
	concoord replica -o concoord.object.bank.Bank -a 127.0.0.1 -p $PARENTPORT
fi