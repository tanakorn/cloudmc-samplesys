#!/usr/bin/env bash

dmck_dir=/home/jeff/Huawei/SAMC-IPC
classpath=$dmck_dir/bin
lib=$dmck_dir/lib
for j in `ls $lib/*.jar`; do
  classpath=$classpath:$j
done
export CLASSPATH=$CLASSPATH:$classpath

java -cp $CLASSPATH -Dlog4j.configuration=mc_log.properties -Ddmck.dir=WORKING_DIR edu.uchicago.cs.ucare.samc.scm.SCMRunner ./scm.conf