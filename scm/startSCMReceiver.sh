#!/usr/bin/env bash

if [ $# -ne 3 ]; then
  echo "usage: startSCMReceiver.sh <ipc_dir> <dmck_dir> <peer_nodes>"
  exit 1
fi

ipc_dir=$1
dmck_dir=$2
peer_nodes=$3

java -cp $CLASSPATH -Dnode.log.dir=WORKING_DIR/log/0 -Dlog4j.configuration=scm_log.properties edu.uchicago.cs.ucare.samc.scm.SCMReceiver $ipc_dir $dmck_dir $peer_nodes &
echo "0:"$! >> pid_file
