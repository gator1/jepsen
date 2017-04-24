# jepsen.kafka

A Clojure library designed to ... well, that part is up to you.

There is a local version of docker compose in the local docker subdirectory
It can be invoked by the following:

    ./up.sh
    
Once all the nodes are up, you can connect to the control node via:
    
    docker exec -it jepsen-control bash

The just
    
    lein test
    
in kafka subdirectory in order to execute the test

## Usage

lein test

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
