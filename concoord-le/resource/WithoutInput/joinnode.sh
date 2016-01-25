#!/usr/bin/env bash

#PARENTPORT is the port number that is started first from start1node.sh
PARENTPORT=14000
#PORT is the port number of new node that we want to join to the cluster in the default example 14001 using localhost
PORT=14001

echo "Executing command :concoord replica -o concoord.object.bank.Bank -b 127.0.0.1:"$PARENTPORT "-a 127.0.0.1 -p " + $PORT
concoord replica -o concoord.object.bank.Bank -b 127.0.0.1:$PARENTPORT -a 127.0.0.1 -p $PORT