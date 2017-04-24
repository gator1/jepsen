Dockerized Jepsen
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
