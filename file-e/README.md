# file-e

A Clojure library designed to ... well, that part is up to you.

## Usage

You can run all tests by executing

    lein test
    
All of the results and analytics can be nicely viewed by executing
   
    lein run serve
    
in your docker compose container.  This website should be visible via the url:
    
    http://172.18.0.8:8080
    
all of your test runs since the creation of the container will be there with full
logging and analytical info.  There is a chance the ip # is changed (although it has
always been the same for me), and if so it can be determined by installing net-tools 
(apt-get install net-tools) in the control node and then running the command

    ifconfig
    
This utility will tell you the ip # of your running container.  You will see a full
set of analytics files like those in the attached [zipfile](20170428T015646.000Z.zip)    

FIXME

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
