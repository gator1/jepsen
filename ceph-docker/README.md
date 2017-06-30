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

control/Dockerfile made a directory /root/test-cluster and the control node's shell is there when you 'docker exec'. 

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

### Create a block device, this doesn't work, probably need a rbd driver plugging.


```console
$ ssh client
root@client:~# rbd create foo --size 4096 -m mon1 --image-feature layering
root@client:~# rbd map foo --pool rbd --name client.admin -m mon1   # hang up here 
root@client:~# mkfs.ext4 -m0 /dev/rbd/rbd/foo
root@client:~# mkdir /mnt/ceph-block-device
root@client:~# mount /dev/rbd/rbd/foo /mnt/ceph-block-device
```

see https://github.com/yp-engineering/rbd-docker-plugin/issues/1 
for more info

```
probably due to client privilege (or lack of it) the above doesn't work in a
container. However all is not lost. We can do it from the host.

```

### Host as a ceph client 
The host is network connected to the containers through a bridge. 
'docker network ls' shows all the networks for docker. docker compose by default
built a separate bridge using project name. The host can tcp communicate with
all the containers. So host can do all the rbd stuff. 
'docket network inspect yourbridge' is used to find mon1's ip address.
Install ceph-client on the host: 'sudo apt-get install ceph-common'  
Get the ceph info "sudo docker cp ceph-client:/etc/ceph /etc", get the
ceph.conf and keyring in host's /etc/ceph  

su -  
rbd create bar --size 4096 -m 172.18.0.8  --image-feature layering # 172.18.0.8 is ip # of mon1, it may differ for you  
rbd map bar --pool rbd --name client.admin -m 172.18.0.8  # 172.18.0.8 is ip # of mon1, it may differ for youi

mkfs.ext4 -m0 /dev/rbd/rbd/bar  
mkdir /mnt/ceph-block-bar  
mount /dev/rbd/rbd/bar /mnt/ceph-block-bar  
  

It should work and you can see 
root@ceph-docker:~# ls -l /dev/rbd/rbd # actual results may vary
total 0 
lrwxrwxrwx 1 root root 10 May  1 12:02 a -> ../../rbd6 
lrwxrwxrwx 1 root root 10 May  1 12:38 bar -> ../../rbd8 
lrwxrwxrwx 1 root root 10 May  1 12:32 foo -> ../../rbd7 

### To run jepsen ceph test from  cleint
Magically this can be seen from mon1 etc and client so you can run jepsen test
on client one as you originally planned. 
