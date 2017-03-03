# ceph-base

Ceph base image (CentOS (latest) with the latest Ceph release installed).

## Docker Hub/Registry location

<https://registry.hub.docker.com/u/ceph/base/>

## Usage (example)

```bash
docker run -i -t gators/ceph-mgmt
```

## Set up 


Files from redhat free tutorial lab about ceph
https://redhat.qwiklab.com/focuses/56

https://bitbucket.org/garyxia/docker
in bitbucket because the files size exceeds github limit.
RHCS2-repo-server.repo
RPM-GPG-KEY-redhat-release
rhceph-2.0-rhel-7-x86_64.iso
rhscon-2.0-rhel-7-x86_64.iso

use this guide to set up
https://docs.oracle.com/cd/E37670_01/E37355/html/ol_create_repo.html

this doesn't seem to work
Create an entry in /etc/fstab so that the system always mounts the DVD image
after a reboot.

so build everything up from /mnt1 and copy over

1. git clone https://garyxia@bitbucket.org/garyxia/docker.git
  @ /root for instance

2. mkdir -p /mnt1/rhcs2
   mkdir -p /mnt1/rhscon2

3. mount -o loop,ro /root/docker/rhceph-2.0-rhel-7-x86_64.iso /mnt1/rhcs2
   mount -o loop,ro /root/docker/rhscon-2.0-rhel-7-x86_64.iso /mnt1/rhscon2

4. rm -rf /mnt
   cp -r /mnt1 mnt
   copy to /mnt so it sticks. 

4. cp /root/docker/RHCS2-repo-server.repo /etc/yum.repos.d
   cp /root/docker/RPM-GPG-KEY-redhat-release /etc/pki//etc/pki/rpm-gpg

4 yum clean all
  yum repolist
