# Jepsen.RDS

Jepsen is a framework to enable testing the accuracy of Consistency and Availability guarantees while undergoing
problems with network partitions of various sorts.  As such, it is most commonly used to evaluate multi-server
systems.  The most common target of such testing is various multi-server node distributed systems.

MySQL and Postgres are a great relational SQL server databases with full ACID guarantees.  
They are a popular choice for smaller
but still decent sized websites and applications where full linear scaling isn't required.  With respect to
the CAP theorem, they are commonly thought of as a CP system since all writes are performed on a single primary
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
In CAP theoretical terms, you encountered a Partition, and Consistency suffered.
 
![Failed Commit](images/failed.jpg?raw=true "Failed Commit")
 
We can use the Jepsen framework to assess the likelihood of this problem occuring under various circumstances.  
We have done so for a couple of scenarios using Postgres, and this could be extended for a full range of load and 
network conditions for any single node cloud database.  By doing so, we can take steps to make and measure 
improvements in this area as well as compare that probability to competitive products.
