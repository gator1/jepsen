# block

A Clojure library designed to ... well, that part is up to you.

## Usage

FIXME

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
