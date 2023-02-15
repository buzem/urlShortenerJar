URL Shortener Client
====================

Our client implementation is built on top of the YCSB benchmark client (see below).

Use during the exercise (you need Maven 3 and Java 1.8 installed):
   
    git clone https://gitlab.db.in.tum.de/per.fuchs/cloud-based-data-processing-url-shortener-client.git
    cd cloud-based-data-processing-url-shortener-client
    
    mvn clean package
    
    ./bin/ycsb run urlshortener -P <workload> -thread 5 -p servers=<server:port_to_connect_to>
    
We provide two workload `workloadurlshortener_debug` and `workloadurlshortener`. The first one runs only a few operations, the second one
much more on a bigger dataset. You should start you program with an empty database and load the fitting database CSV file before running the
workload. The CSV file for the debug workload is included.

You generate the CSV files for the other workloads yourself by running:

    ./bin/ycsb load csv -P <workload> -thread 1
    

The workload files are simple Java property files, so you can generate your own workloads to test only parts of your system, e.g. 
read or write only workloads. You can also run the client single threaded for testing to exclude concurrent operations.

We still work on a real stresstest workload which we provide in a later phase of the project.   

The client reports the status of each operation run as well as the latencies for all operations.
A "correct" client and url shortener service execution reports only `Return=OK` if you disable your `Deletion Service`.
With a working `Deletion Service` you might see some `Return=NOT_FOUND` operations.
A operation `Return=UNEXPECTED_STATE` or any other return codes are incorrect operations which means your code or the client are buggy.
Please, report any bugs via the issue tracker of the repository.
  
Later you might want to use:

    ./bin/ycsb run urlshortener -P <workload> -target <targeted_number_of_ops_per_second> -threads <threads_to_generate_load>

Background
----------

The client uses a sequential counter which it hashes to URLs and then to aliases to keep track on what key is supposed to be in your 
database. Therefore, it does not work if you use a different algorithm to generate the aliases and has no knowledge about the expired
keys. However, it checks if you return the correct URL if you don't return a 404.

Run on an Amazon EC2 instance
=============================

Starting a new EC2 instance
---------------------------

Steps:
1. Sign into AWS Educate Account
2. Click on AWS Starter Account, you will be redirected to Vocareum
3. Accept Terms and Conditions
4. Click on AWS Console (probably works better with Chrome under Linux)
5. Unter `Erstellen einer Loesung` click on `Virtuelle Maschine starten mit EC2`
6. Choose AMI: `Amazon Linux 2 AMI`
7. Choose Instance Type: `t2 micro`
8. Choose Add storage and add 30 GB of `gp2` EBS storage
9. Click review and launch
10. Create a new key-pair for Amazon, you will be asked in a dialogue
11. Download and store newly created file on your machine
12. Wait for instance to start
13. **Do not forget to shutdown instances after usage**
14. **Do not forget to delete EBS volumes once you finished this exercise**

How to SSH onto an EC2 instance
-------------------------------

Steps:
1. Run `chmod 400 <pem_file>` on your machine
2. Open the running instances window: https://console.aws.amazon.com/ec2/v2/home?region=us-east-1#Instances:instanceState=running
3. Run `run ssh -i "<pem_file>"  ec2-user@<ip-address-from-running-instances>`


Running the client
------------------
We use the packaged version for running on Amazon EC2 instances. To build the client run and upload the client run
 
    mvn package -DskipTests
    scp -i /path/my-key-pair.pem distribution/target/ycsb-0.18.0-SNAPSHOT.tar.gz ec2-user@<ip-address-from-running-instances>
    
To unpack and run the client on an EC2 instance log onto the instance as explained above and run:

    sudo yum install java
    tar -zxvf ycsb-0.18.0-SNAPSHOT.tar.gz
    ./bin/ycsb run urlshortener -P workloads/workloadshortener
    
Troubleshooting
---------------

https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AccessingInstancesLinux.html
https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connection-prereqs.html

If you get cryptic error message trying to start an EC2 machine, you might chose something that is not `Free tier eligble`. 
Choose only `Free tier eligble` components for now.
    
YCSB
====================================
<!--
Copyright (c) 2010 Yahoo! Inc., 2012 - 2016 YCSB contributors.
All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You
may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. See accompanying
LICENSE file.
-->



Links
-----
* To get here, use https://ycsb.site
* [Our project docs](https://github.com/brianfrankcooper/YCSB/wiki)
* [The original announcement from Yahoo!](https://labs.yahoo.com/news/yahoo-cloud-serving-benchmark/)

   
  See https://github.com/brianfrankcooper/YCSB/wiki/Running-a-Workload
  for a detailed documentation on how to run a workload.

  See https://github.com/brianfrankcooper/YCSB/wiki/Core-Properties for 
  the list of available workload properties.


Building from source
--------------------

YCSB requires the use of Maven 3; if you use Maven 2, you may see [errors
such as these](https://github.com/brianfrankcooper/YCSB/issues/406).

To build the full distribution, with all database bindings:

    mvn clean package

To build a single database binding:

    mvn -pl site.ycsb:mongodb-binding -am clean package
