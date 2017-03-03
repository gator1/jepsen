Dockerized Jepsen
=================

This docker image attempts to simplify the setup required by ceph.
It is intended to be used by a CI tool or anyone with docker who wants to try ceph and jepsen themselves.

It contains a multi-nodes ceph on docker setup. It uses [docker-in-docker](https://github.com/gators/dind16, cloned from jpetazzo) to all nodes for ceph and a jepsen controller. The set up is a mgmt, three monitors (mon-node1-3) and three OSDs (osd-node1-3). 
I am still having issues with ssh. 
The dind uses ubuntu. For ceph since I got the most of ideas from redhat lab on
line tutorial, centos is the one for ceph. Now this is saved as is, dind uses
ubuntu and the ceph nodes are centos. In the process to try to change dind to
centos. 

To start run (note the required --privileged flag)
Got my own with the jepsen latest from gator1, me!

````
    docker run --privileged -t -i gators/ceph
    or
    docker run -v /home/gary/docker:/docker --privileged -t -i gators/ceph
    the above map local /home/gary/docker to container's /docker
````

Building the docker image
=========================

Alternatively, you can build the image yourself. This is a multi-step process, mainly because [docker doesn't let you build with --privileged operations](https://github.com/docker/docker/issues/1916)

1.  From this directory run 

    ````
	docker build -t ceph .
    ````

2.  Start the container and run build-dockerized-jepsen.sh

    ````
    docker run --privileged -t -i ceph

    > build-dockerized-ceph.sh
    ````

3.  From another window commit the updated image

    ````
    docker commit {above container-id} ceph
    ````
    
4.  With the final image created, you can create a container to run ceph from

    ```
    docker run --privileged -t -i ceph
    ```

U docker in docker (dind) has docker version 1.5 which can no longer pull images from docker hub 
=========================
    docker save tutum/debian:jessie > debian.tar to save the image into a file.
    after the docker build and run, ssh or -v the debain.tar on the running
    docker container and do docker load (less) debian.tar and  load the image.
    retag if necessary

    changed this to centos7 for ceph. to bring up the containers works. 
    for ceph nodes, base is the basic centos 7 which is good to test about bring
    up in dind, the size is small. mgmt is from the same centos but have ceph cd
    copied to /mnt and yum ready

