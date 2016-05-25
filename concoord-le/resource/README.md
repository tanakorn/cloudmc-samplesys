README
-----------------
STARTING 
-----------------

This is the shell script to run concoord, in order to use 
1. go to path /../concoord/shellscript 
2. execute ./start1node.sh PARENTPORT number 
	e.g. ./start1node 14000 
	This command will return 1 node with PARENTPORT
3. execute ./joinnode.sh PARENTPORT PORTNUMBER 
	e.g. ./joinnode 14000 14001 to start a follower node in port 14001 to join parent node with port 14000

If you want to edit the parent node please edit the PARENTPORT to the port number you desired
If you want to edit the port of follower node in a cluster please make sure that the PARENTPORT
variable in start1node.sh and joinnode.sh are equal. Next make sure that the variable PORT is a 
free port number

-----------------
TERMINATING
-----------------

To end a single node please use ./kill1node.sh PORTNUMBER e.g. ./kill1node 14000
Change the variable PORT to determine which port number of nodes you want to terminate

