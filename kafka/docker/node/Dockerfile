FROM ubuntu:16.04
MAINTAINER jake@apache.org

RUN apt-get -y -q update
RUN apt-get install -qqy sudo net-tools wget curl vim man faketime ntpdate unzip iptables psmisc tar bzip2 libzip-dev iproute rsyslog iputils-ping logrotate # sysvinit-core sysvinit sysvinit-utils

RUN apt-get -y -q install software-properties-common
RUN add-apt-repository ppa:openjdk-r/ppa
RUN apt-get -y -q update
RUN apt-get install -qqy openjdk-8-jdk

# Install packages
RUN apt-get update && \
    apt-get -y install openssh-server pwgen && \
    mkdir -p /var/run/sshd && \
    sed -i "s/PermitRootLogin.*/PermitRootLogin yes/g" /etc/ssh/sshd_config
    #sed -i "s/UsePrivilegeSeparation.*/UsePrivilegeSeparation no/g" /etc/ssh/sshd_config

RUN apt-get -y -q install zookeeper=3.4.8-1 zookeeper-bin=3.4.8-1 zookeeperd=3.4.8-1

ARG kafka_version=0.10.2.0
ARG scala_version=2.12

ENV KAFKA_VERSION=$kafka_version SCALA_VERSION=$scala_version
ADD download-kafka.sh /tmp/download-kafka.sh
RUN apt-get -y -q install jq
RUN chmod a+x /tmp/download-kafka.sh && sync && /tmp/download-kafka.sh #&& tar xfz /tmp/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz -C /opt && rm /tmp/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz && ln -s /opt/kafka_${SCALA_VERSION}-${KAFKA_VERSION} /opt/kafka
# We run this in clojure setup to get fresh install.
#&& tar xfz /tmp/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz -C /opt && rm /tmp/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz && ln -s /opt/kafka_${SCALA_VERSION}-${KAFKA_VERSION} /opt/kafka

ADD set_root_pw.sh /set_root_pw.sh
ADD run.sh /run.sh
RUN chmod +x /*.sh

ENV AUTHORIZED_KEYS **None**

EXPOSE 22
CMD ["/run.sh"]





