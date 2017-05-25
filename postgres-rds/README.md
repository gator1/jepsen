# Jepsen.Postgres

Jepsen is a framework to enable testing the accuracy of Consistency and Availability guarantees while undergoing
problems with network partitions of various sorts.  As such, it is most commonly used to evaluate multi-server
systems.  The most common target of such testing is various multi-server node distributed systems.
Postgres is a great relational SQL server database with full ACID guarantees.  It is a popular choice for smaller
but still decent sized websites and applications where full linear scaling isn't required.  With respect to
the CAP theorem, it is commonly thought of as a CP system since all writes are performed on a single primary
node.  Because the database is ACID compliant, the assumption is that things are always consistent.  What is
overlooked is the fact that it isn't only the server's knowledge that matters.  In a client server system, if
the client and the server don't agree on what happened, there is a problem.

But how can that occur?

A simple but readily reproducible example should suffice.  You are buying something on a website.
When you click on "Submit" on the final ordering page, you wait and you wait.  Finally the page times
out.  Thinking the transaction failed, you re-submit it again.  Later you discover that your card got 
charged twice minutes apart, and unhappiness results.  If you are lucky you notice it and call the credit
card or ecommerce retailer to fix the problem.  If you don't, you may end up getting double-charged for
your order, or even getting the same order twice.  

Well one way to look at it is that any client server interaction is a special case of a two node
cluster.  ACID consistency defines serializability and consistency guarantees with respect to the
server's response to various clients requests.  But it doesn't address the issue of the client and
the server disagreeing on what took place.  It is entirely possible that a transaction succeeded, but due
to a network problem, the successful confirmation got lost as a result.  The client would think it failed then.
In CAP theoretical terms, you encountered a Partition, and Consistency suffered.  The ugly truth of the matter
is that the basic transaction API's do nothing to address this problem, and clients generally don't try to fix or
address the problem.
 
![Failed Commit](images/failed.jpg?raw=true "Failed Commit")
 
So let's see what we can do to demonstrate and reproduce this problem in the Jepsen framework.   You can
get a copy of this project from 
 
    git clone https://github.com/khdegraaf/jepsen
    
Once you have a clone, cd into the docker subdirectory and run

    ./up.sh

once that is finished, it will give you a command line to execute to enter the docker compose environment we will be
running in

    docker exec -it jepsen-control bash

once you are in docker, you will start at the top level jepsen subdirectory so cd into the postgres-rds subdirectory and run
    
    lein run test
    
## Test #1 (very slow network)    
    
In order to maximize the chance of catching these in transit errors, we have configured our code to
slow down the network by 0.5sec for each network message, and run with 40 concurrent worker threads to increase
the number of requests and the number of chances for a problem.  If everything goes correctly, you will see a
history log like the following
     
![Screenshot #1](images/Screen1.png?raw=true "Screenshot #1")
     
Every 10 seconds and for 10 seconds, we will start and then stop the nemesis process, which will cut off all network
communication between the client node (called control) and the server node (called n1 here).  The result will be errors
and results like the following
     
![Screenshot #2](images/Screen2.png?raw=true "Screenshot #2") 
    
After cycling through this for 60 seconds, it will end up with the following log
    
![Screenshot #3](images/Screen3.png?raw=true "Screenshot #3")

and then the results

![Screenshot #4](images/Screen4.png?raw=true "Screenshot #4") 
   
As you can see from the results, 1364 writes were attempted, and 240 of these were unacknowledged.  Of those 240
unacknowledged results, 197 of them were failures.  But 43 of them were recovered when all of the written data
was examined at the end.  These 43 cases represent the possibility of our problem scenario, albeit with a perhaps
exaggerated probability due to slowing down the network.  But network partitions in the real world could approach 
this, it depends on network latency, as well as the size and latency of the database transaction in the
application itself.  

## Test #2 (very fast network)

If we push this to the highest possible throughput by dropping out all network slowness we will see
best case results.  This is because our test database transaction consists of inserting a single integer 
rather than a more complex multi value read, modify and write transaction that might be more representative
of an actual credit card charge in real life.  We might see something like this

![Screenshot #5](images/Screen5.png?raw=true "Screenshot #5")

The attempted writes will be much more numerous due to lowered latency, while the unacknowledged count will remain
the same, as it is determined by the length of the network failure, divided by the timeout interval times the number
of concurrent threads.  But as you will see, the number of false negatives is significantly reduced down to 3.  But
a round-trip transaction cost of only 10ms is pretty unrealistic.  There is little to no network latency running on
a local network in docker.

## Test #3 (realistic network)

In a cloud hosted environment, a more realistic transaction request latency might be more like 100ms plus whatever
time the database takes to do the commit.  If we modify the network slowness to 100ms, we will get 200ms round-trip 
latency and something like the following results.
    
![Screenshot #6](images/Screen6.png?raw=true "Screenshot #6")

Here are some framework generated graphs for this scenario that have more throughput numerics

![Latency Quantiles #1](images/latency-quantiles1.png?raw=true "Latency Quantiles #1")

and

![Latency Raw #1](images/latency-raw1.png?raw=true "Latency Raw #1")

and

![Throughput Rate #1](images/rate1.png?raw=true "Throughput Rate #1")

## Conclusion

But this problem is fixable.  If a transaction, rather than being a single distributed commit call, instead
performed the commit in two phases, a prepare phase that confirmed that it was possible and performed any needed
locks, and then a second phase to actually do the submission (especially if put in a highly available distributed
queue) and an idempotent commit via id that can be blindly retried until fully confirmed.  But there aren't any
standard out of the box libraries to do this, and clients generally don't go to the effort.  

So in the meanwhile, reducing the window for problems is the main practical step cloud or database vendors can do.
We can use the Jepsen framework to assess the likelihood of this problem occuring under various circumstances.
By doing so, we can take steps to make and measure mprovements in this area as well as compare that probability to 
competitive products.


## 
