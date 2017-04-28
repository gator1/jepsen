# file-e

A Clojure library designed to ... well, that part is up to you.

## Usage

You can run all tests by executing

    lein test
    
This will run all of the tests together in the old test file.  Note that the main
function (in this project in core.clj) enables running a single test via
    
    lein run test
    
The benefit of using this is a full set of command line parameters are enabled, and
you can override various settings.  See
    
    lein run test --help
    
for full documentation:
 
    -h, --help                                             Print out this message and exit
    -n, --node HOSTNAME             [:n1 :n2 :n3 :n4 :n5]  Node(s) to run test on
      --nodes-file FILENAME                              File containing node hostnames, one per line.
      --username USER             root                   Username for logins
      --password PASS             root                   Password for sudo access
      --strict-host-key-checking                         Whether to check host keys
      --ssh-private-key FILE                             Path to an SSH identity file
      --concurrency NUMBER        1n                     How many workers should we run? Must be an integer, optionally followed by n (e.g. 3n) to multiply by the number of nodes.
      --test-count NUMBER         1                      How many times should we repeat a test?
      --time-limit SECONDS        60                     Excluding setup and teardown, how long should a test run for, in seconds? 
          
One thing to beware of is that the --concurrency default of 1n will override any
concurrency setting you specify in your test map (and lein test won't).  So if you need
a different number, you must specify it via the command line when using lein run test.        
    
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
set of analytics files like those in the attached [link](20170428T015646.000Z.zip).
if you are running in a virtual machine, you will browse to whatever ip # the control
node is, or localhost if you have a browser on your control node.

FIXME

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
