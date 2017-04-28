# jepsen.kafka

A Clojure library designed to ... well, that part is up to you.

There is a local version of docker compose in the local docker subdirectory
It can be invoked by the following:

    ./up.sh
    
Once all the nodes are up, you can connect to the control node via:
    
    docker exec -it jepsen-control bash

The just
    
    lein test
    
in kafka subdirectory in order to execute the test.  You can freely run it multiple
times to reproduce failures by just running

    lein test
    
again. This project depends on a minor tweaked Jepsen 0.1.2.2 from
[]org.clojars.khdegraaf/jepsen "0.1.2.2"].  It adds :debug info to ops and os/ubuntu
to enable ubuntu in docker nodes.  See source tree in 
https://github.com/gator1/jepsen/tree/master/jepsen  

## Usage

lein test

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
