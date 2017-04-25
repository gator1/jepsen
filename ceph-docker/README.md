Dockerized Ceph (and Jepsen)
=================

This docker image attempts to simplify the setup required by ceph (Jepsen is kept, the reason I want to set up ceph is to jepsen it).
It is intended to be used by a CI tool or anyone with docker who wants to try to set up ceph on dockers and then run jepsen themselves.

It contains all the ceph mon, osd, control jepsen dependencies and code. It uses [Docker Compose](https://github.com/docker/compose) to spin up the mon, osd, client
containers used by ceph, the deploy/admin node sits at control node,  Jepsen ceph also sits at control node.  

To start run

````
    ./up.sh
    docker exec -it jepsen-ceph-control bash
````

After startup, you got control node (admin, master, whatever you want to call it) which you can get into its shell using the above 'docker exec' command.
In addtion to control node, we have mon1, mon2, mon3 for monitor nodes. osd0, osd1, osd2, osd3 for osd nodes (osd3 is not initially deployed so that you can add one later). 
A client node is also privisioned.  All the nodes are root sshable from control node, you can type 'ssh mon1' to mon1 without a password. Take a look at both
control and node's Dockerfile and control's init.sh to see how it was done. 

The following is a step by step guide as how to set up ceph on those nodes manaully. It is possible to set up ceph using ceph-ansible automatically. 
The following guide is based on https://github.com/carmstrong/multinode-ceph-vagrant. 

control/Dockerfile made a directory /root/test-cluster and the control node's shee is there when you 'docker exec'. 

All commands are counducted in /root/test-cluster directory unless otherwise noted.

1. ceph-deploy new mon1 mon2 mon3  
	this creates a ceph.conf among others in /root/test-cluster. 

2. modify ceph.conf to add  
   osd pool default size = 3 # because we want 3 osd disk initially  
   mon_clock_drift_allowed = 1  
   osd max object name len = 256  
   osd max object namespace len = 64  

3. ceph-deploy install --release=kraken control  mon1 mon2 mon3 osd0 osd1 osd2 client  # list all the nodes, this install ceph software  
   this will take some time to finish

4. ceph-deploy mon create-initial #starts monitor (three nodes because of step 1)

5.  ssh osd0  
    mkdir /var/local/osd0 && sudo chown ceph:ceph /var/local/osd0   
    exit  
    ssh osd1  
    mkdir /var/local/osd1 && sudo chown ceph:ceph /var/local/osd1   
    exit  
    ssh osd2  
    mkdir /var/local/osd2 && sudo chown ceph:ceph /var/local/osd2   
    exit  

6.  ceph-deploy osd prepare osd0:/var/local/osd0 osd1:/var/local/osd1 osd2:/var/local/osd2  
    ceph-deploy osd activate  osd0:/var/local/osd0 osd1:/var/local/osd1 osd2:/var/local/osd2  
  
7. ceph-deploy admin control mon1 mon2 mon3 osd0 osd1 osd2 client  

```
may not be necessary, maybe because I use root? anyway if there is complain about keyring, do it.  
```

8. chmod +r ./ceph.client.admin.keyring    
   ssh mon1 chmod +r /etc/ceph/ceph.client.admin.keyring   
   ssh mon2 chmod +r /etc/ceph/ceph.client.admin.keyring   
   ssh mon2 chmod +r /etc/ceph/ceph.client.admin.keyring   


9. ceph health 
 it tells you the ceph cluster with docker is up and running.   

```
root@control:~/test-cluster# ceph health  
HEALTH_OK  
root@control:~/test-cluster# ceph osd tree  
ID WEIGHT  TYPE NAME     UP/DOWN REWEIGHT PRIMARY-AFFINITY   
-1 2.31326 root default                                      
-2 0.77109     host osd0                                     
 0 0.77109         osd.0      up  1.00000          1.00000   
-3 0.77109     host osd1                                     
 1 0.77109         osd.1      up  1.00000          1.00000   
-4 0.77109     host osd2                                     
 2 0.77109         osd.2      up  1.00000          1.00000   
```

10. Congratulations!!!  
