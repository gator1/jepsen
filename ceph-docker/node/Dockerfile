# FIXME: tutum/debian will be deprecated soon: https://github.com/tutumcloud/tutum-debian/blob/master/README.md
#FROM tutum/debian:jessie
#FROM vkill/systemd-sshd
FROM gators/systemd-sshd

ENV AUTHORIZED_KEYS **None**

RUN rm /etc/apt/apt.conf.d/docker-clean && apt-get update && apt-get install -y sudo net-tools wget sudo ack-grep curl vim man faketime unzip iptables iputils-ping logrotate 
#RUN apt-get install -y python && \
#    sed -i "s/UsePrivilegeSeparation.*/UsePrivilegeSeparation no/g" /etc/ssh/sshd_config && \
#    sed -i "s/PermitRootLogin prohibit-password/PermitRootLogin yes/g" /etc/ssh/sshd_config

#ADD ./run.sh /run.sh
#RUN chmod +x /run.sh
#ADD ./set_root_pw.sh /set_root_pw.sh
#RUN chmod +x /set_root_pw.sh


