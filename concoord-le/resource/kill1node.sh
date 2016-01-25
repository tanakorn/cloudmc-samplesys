#!/usr/bin/env bash

#this is the command to kill single node with port number with example PORT 14000 
PORT="$1"
if [ "$PORT" = "" ]
then
	echo "In order to use please do ./kill1node.sh PORTNUMBER e.g. ./kill1node 14000"
	else
	echo "Terminating process at port " $PORT
	fuser -k $PORT/tcp
fi

