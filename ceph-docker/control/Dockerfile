FROM ubuntu:16.04
MAINTAINER jake@apache.org

ENV LEIN_ROOT true

#
# Jepsen dependencies
#
RUN apt-get -y -q update && \
    apt-get -y -q install software-properties-common && \
    add-apt-repository ppa:openjdk-r/ppa && \
    apt-get -y -q update && \
    apt-get install -qqy \
        openjdk-8-jdk \
        libjna-java \
        git \
        gnuplot \
        wget \
        sshpass \
        sudo \
        ack-grep \
	vim # not required by jepsen itself, just for ease of use

RUN wget -q -O- 'https://download.ceph.com/keys/release.asc' | apt-key add - && \
    echo deb http://download.ceph.com/debian/ $(lsb_release -sc) main | tee /etc/apt/sources.list.d/ceph.list && \
    apt-get update && apt-get install -yq ntp ceph-deploy

RUN wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    mv lein /usr/bin && \
    chmod +x /usr/bin/lein && \
    lein self-install

RUN cd / && \
    git clone https://github.com/ceph/ceph-ansible.git

RUN cd /root && \
    mkdir test-cluster

# You need to locate jepsen in this directory (up.sh does that automatically)
ADD jepsen /jepsen
RUN cd /jepsen/jepsen && lein install

ADD ./bashrc /root/.bashrc
ADD ./init.sh /init.sh
RUN chmod +x /init.sh

CMD /init.sh
