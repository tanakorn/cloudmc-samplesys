README
-----------------
STARTING 
-----------------

This is the shell script to run concoord, in order to use 
1. go to path /../concoord/shellscript 
2. execute ./start1node.sh This command will return 1 parent node of concoord 
3. execute ./joinnode.sh to start a cluster

If you want to edit the parent node please edit the PARENTPORT to the port number you desired
If you want to edit the port of follower node in a cluster please make sure that the PARENTPORT
variable in start1node.sh and joinnode.sh are equal. Next make sure that the variable PORT is a 
free port number

If you want to start a whole cluster 
1. go to path /../concoord/shellscript 
2. execute ./startcluster.sh

If you want a cluster more than 3 nodes then please change variable NUMNODE

-----------------
TERMINATING
-----------------

To end a single node please use ./kill1node.sh
Change the variable PORT to determine which port number of nodes you want to terminate

To end a cluster please use ./killcluster.sh
This commands will basically ends all remaining concoord process
