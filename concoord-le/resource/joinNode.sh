#!/usr/bin/env bash

#PARENTPORT is the port number that is started first from start1node.sh
#PORT is the port number of new node that we want to join to the cluster in the default example 14001 using localhost

PARENTPORT=$(($1 + 14000))
PORT=$(($2 + 14000))
if [ "$PARENTPORT" = "" ] && [ "$PORT" = "" ]
then
	echo "In order to use please do ./joinnode.sh PARENTPORT PORTNUMBER e.g. ./joinnode 14000 14001"
else
	echo "Executing command :concoord replica -o concoord.object.bank.Bank -b 127.0.0.1:"$PARENTPORT "-a 127.0.0.1 -p " $PORT
	concoord replica -o concoord.object.bank.Bank -b 127.0.0.1:$PARENTPORT -a 127.0.0.1 -p $PORT
fi

