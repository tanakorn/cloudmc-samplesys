#!/usr/bin/env bash

#this is the command to kill all node within cluster that are started from concoord
ps -ef | grep concoord | grep -v grep | awk '{print $2}' | xargs kill
