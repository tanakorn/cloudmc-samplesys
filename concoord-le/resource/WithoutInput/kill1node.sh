#!/usr/bin/env bash

#this is the command to kill single node with port number with example PORT 14000 
PORT=14000

fuser -k $PORT/tcp