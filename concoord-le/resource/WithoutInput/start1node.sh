#!/usr/bin/env bash

#PARENTPORT is going to be the parent node that will be joined by other. In default example 14000 using localhost
PARENTPORT=14000

echo "Executing command :concoord replica -o concoord.object.bank.Bank -a 127.0.0.1 -p " $PARENTPORT
concoord replica -o concoord.object.bank.Bank -a 127.0.0.1 -p $PARENTPORT