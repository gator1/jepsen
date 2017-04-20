# block

A Clojure library designed to test ceph as a block device. The basic test works and ceph block works well.
No inconsistence and availavlity with 100ms cut of an osd node is good. 

The setup of ceph is often diffcult. The best of ceph setup is through vagrant virtualbox. 

https://github.com/carmstrong/multinode-ceph-vagrant

rbd mapping needs layering

rbd create --size 4096 docker_test --image-feature layering

ceph-docker and ceph-ansible are more difficult to setup. ceph docker is NOT for setting a bunch of docker containers to run ceph. It's to set up a bunch of VMs while the VMs run the ceph/daemon containers. 

https://github.com/ceph/ceph-ansible/blob/master/vagrant_variables.yml.sample#L4 helps set up vm and this to set up ceph docker in the VMS

https://www.sebastien-han.fr/blog/2016/02/08/easily-deploy-containerized-ceph-daemons-with-vagrant/

the osd nodes' docker run exited, had to do it manually

vagrant@ceph-osd2:~$ sudo docker run -d --net=host -v /etc/ceph:/etc/ceph -v /var/lib/ceph:/var/lib/ceph -v /dev:/dev --privileged=true -e OSD_FORCE_ZAP=1 -e OSD_DEVICE=/dev/sdb ceph/daemon osd_ceph_disk



## Usage

lein test

## Issues

The main issue is ssh access to the test nodes. The name is hardcoded now. 



vagrant ssh n0
sudo su -
passwd root
sudo /etc/init.d/ssh restart

ssh-copy-id cephuser@mon-node1
ssh copy id ssh id copy ssh id cpy ssh cpy id
/etc/init.d/rsyslog restart
jepsen root/root is hardcoded in the code


need to set up ssh key so that ssh root@n1 and shh vagrant@n1 won't ask questions or password:

from jepsen control node:
ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa
ssh-copy-id root@n1

Better way is not to use n1 rather the real ceph node name such as osd-1


## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
