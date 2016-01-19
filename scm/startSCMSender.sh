#!/usr/bin/env bash

if [ $# -ne 3 ]; then
  echo "usage: startSCMSender.sh <ipc_dir> <dmck_dir> <nodeId>"
  exit 1
fi

ipc_dir=$1
dmck_dir=$2
nodeId=$3

java -cp $CLASSPATH -Dnode.log.dir=WORKING_DIR/log/$nodeId -Dlog4j.configuration=scm_log.properties edu.uchicago.cs.ucare.samc.scm.SCMSender $ipc_dir $dmck_dir $nodeId &
echo $nodeId":"$! >> pid_file
