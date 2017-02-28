#!/bin/sh

#gen sshkey
ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa

#start one node and install deps
docker run -d --name n1 -e ROOT_PASS="root" -e AUTHORIZED_KEYS="`cat ~/.ssh/id_rsa.pub`" gators/ceph-centos-base
N1_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' n1)

sleep 10

#ssh $N1_IP "rm /etc/apt/apt.conf.d/docker-clean && apt-get update && apt-get install sudo net-tools wget sysvinit-core sysvinit sysvinit-utils curl vim man faketime unzip iptables iputils-ping logrotate && apt-get remove -y --purge --auto-remove systemd" 

docker export n1 > /root/cephnode.tar
gzip /root/cephnode.tar

