#!/usr/bin/env bash

#PARENTPORT is going to be the parent node that will be joined by other. In default example 14000 using localhost
PARENTPORT=14000
# NUMNODE is the variable that indicates the number of follower node we want in a cluster
NUMNODE=2

echo "Executing command :concoord replica -o concoord.object.bank.Bank -a 127.0.0.1 -p " $PARENTPORT
concoord replica -o concoord.object.bank.Bank -a 127.0.0.1 -p $PARENTPORT &

#This example cluster is running with 3 nodes with PORT: 14000, 14001, 14002
PORT=$PARENTPORT

for (( c=1; c<=$NUMNODE; c++ ))
do
	PORT=$(($PORT+1))
	#Code to make sure port available by terminating other process that runs on the desired port number
	fuser -k $PORT/tcp
	#Start to run a follower node 
	echo "Executing command :concoord replica -o concoord.object.bank.Bank -b 127.0.0.1:"$PARENTPORT "-a 127.0.0.1 -p " $PORT
	concoord replica -o concoord.object.bank.Bank -b 127.0.0.1:$PARENTPORT -a 127.0.0.1 -p $PORT &
done 