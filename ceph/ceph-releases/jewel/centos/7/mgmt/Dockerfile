# CEPH BASE IMAGE
# CEPH VERSION: Jewel
# CEPH VERSION DETAIL: 0.94.x

#FROM centos:7
FROM ceph/base:tag-build-master-jewel-centos-7
MAINTAINER gator1 "gator1@github.com"

ENV ANSIBLE_GIT_URL https://github.com/ceph/ceph-ansible.git

# Install prerequisites
RUN yum install -y unzip net-tools  git which redhat-lsb-core

RUN yum install -y openssh-server openssh-clients openssl-libs iproute
#RUN yum install -y pwgen

#RUN useradd -ms /bin/bash ceph
#RUN echo "ceph:ceph" | chpasswd
#RUN usermod -a -G root ceph
#RUN usermod -g wheel ceph

#ADD entrypoint.sh /entrypoint.sh
#ENTRYPOINT ["/entrypoint.sh"]

ADD set_root_pw.sh /set_root_pw.sh
ADD run.sh /run.sh
RUN chmod +x /*.sh

ENV AUTHORIZED_KEYS **None**

RUN mkdir /var/run/sshd
RUN sed -i "s/UsePrivilegeSeparation.*/UsePrivilegeSeparation no/g" /etc/ssh/sshd_config
RUN ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key -N ''
RUN ssh-keygen -t ecdsa -f /etc/ssh/ssh_host_ecdsa_key -N ''
RUN ssh-keygen -t ed25519 -f /etc/ssh/ssh_host_ed25519_key -N ''

#RUN /usr/sbin/sshd -D
RUN rm -rf /mnt
COPY mnt /mnt
COPY docker/RHCS2-repo-server.repo /etc/yum.repos.d
COPY docker/RPM-GPG-KEY-redhat-release /etc/pki/rpm-gpg
COPY docker/rhceph-2.0-rhel-7-x86_64.iso /root
COPY docker/rhscon-2.0-rhel-7-x86_64.iso /root

RUN git clone $ANSIBLE_GIT_URL /root/ceph-ansible  
#RUN yum clean all
#RUN yum repolist

EXPOSE 22
CMD ["/run.sh"]

