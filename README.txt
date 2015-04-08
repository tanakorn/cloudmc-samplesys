Description

This is a demo project for ClooudMC model checker. The program 'LeaderElection' does
only leader election. When you start multiple processes of LeaderElection, these
processes talk with each other to elect a leader, and become idle.

We integrate ClooudMC to the LeaderElection. The model checker will run the cluster
of LeaderElection program and model check it.


Compiling

1. Download and install AspectJ, more detail can find here,
https://eclipse.org/aspectj/

2. Set environment variable ASPECTJ_LIB to point to the AspectJ library path, 
e.g.
export ASPECTJ_LIB="/path/aspectj/lib"

3. Set Java classpath to include the AspectJ library (all AspectJ jar files).
e.g.
CLASSPATH="$CLASSPATH:$ASPECTJ_LIB/aspectjrt.jar"
CLASSPATH="$CLASSPATH:$ASPECTJ_LIB/aspectjtools.jar"
CLASSPATH="$CLASSPATH:$ASPECTJ_LIB/aspectjweaver.jar"
CLASSPATH="$CLASSPATH:$ASPECTJ_LIB/org.aspectj.matcher.jar"
export CLASSPATH

4. Run ant to compile the LeaderElection program integrating with ClooudMC.


Running

1. Go to mcdeploy.

2. Open mcsystem.conf with a text editor. The mcsystem.conf is a config file for
model checker.
  - Change working_dir variable to a path you want model checker to work. The
    model checker will store every file here (target system config file, log,
    etc.)
  - Change num_node to be the number of node of LeaderElection we want to test

3. Go to mcdeploy/resource

4. Open testrunner.sh with a text editor. This is a shell script to run the
model checker.
  - Change `leader_election` variable assignment to the path of this project.

5. Go to mcdeploy again.

6. Run setup script (i.e. `./setup`), this is a shell script that setup
environment for running model checker in the working directory you specified in
mcsystem.conf

7. Run lermi script (i.e. `./lermi`), this is a shell script that run
rmiregistry with class path specifying.

8. Go to the working directory that you have specified in mcsystem.conf

9. Run monitorcheck.sh (i.e. `./monitorcheck.sh`), now model checker will keep
running until it explores all paths.

10. In a log file, mc.log, there should not be ERROR line, and it should keep
informing the result this means that the setup is fine


Code

The aspect for LeaderElection program can be found here,
src/edu/uchicago/cs/ucare/example/election/LeaderElectionAspect.aj 

When a LeaderElection node send a packet to other nodes, the aspect intercepts
it and informs to model checker server. High level LeaderElection logic thinks
that it have done send the packet, but actually, the aspect keeps the packet
with itself. If model checker server decides to make that packet to be really
sent, it calls callback to the sender node, the aspect, and it really sends the
packet this time. When the receiver node gets the packet, it acknowledges back
to model checker. Every communication between LeaderElection program and model
checker server is done by RMI

