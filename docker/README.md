Dockerized Jepsen
=================

This docker image attempts to simplify the setup required by Jepsen.
It is intended to be used by a CI tool or anyone with docker who wants to try jepsen themselves.

Run tmux in the docker image to get multiple Windows
=========================

    install tmux in the image for multiple terminals (kind of)
    sudo apt-get install tmux

    issue:
    tmux

    then resize your window to be double width.

    Then:

    CTRL+b, %

    Then you can use:
    CTRL+b, <- and ->

    (Left arrow and right arrow)

    to switch windows.

=========================

It contains all the jepsen dependencies and code. It uses [Docker Compose](https://github.com/docker/compose) to spin up the five
containers used by Jepsen.  

To start run

````
    ./up.sh
    docker exec -it jepsen-control bash
````

From the control node, you can try etcd demo following [README](https://github.com/jepsen-io/jepsen/blob/master/doc/scaffolding.md)

```
cd /jepsen/etcdemo
```
