#!/usr/bin/env bash

leader_election=/home/jeff/Huawei/SAMC-IPC
classpath=.:$leader_election/bin
lib=$leader_election/lib
for jar in `ls $lib/*.jar`; do
  classpath=$classpath:$jar
done

export CLASSPATH=$CLASSPATH:$classpath
export PATH=$PATH:bin/

java -Dlog4j.configuration=mc_log.properties -Delectiontest.dir=WORKING_DIR edu.uchicago.cs.ucare.samc.server.LeaderElectionRunner ./target-sys.conf

