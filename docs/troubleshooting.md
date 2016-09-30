---
layout: base
title: Troubleshooting
---

### General

* AWS VPC / Classic
  
  It's recommended that you use [VPC mode](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-vpc.html) to run 
  Clocker 2 within [AWS](http://aws.amazon.com/). This is because problems have been recorded with DNS and starting 
  instances withn AWS Classic. For more information on doing this, see the brooklyn docs 
  [here](http://brooklyn.apache.org/v/latest/ops/locations/index.html#ec2-classic-problems-with-vpc-only-hardware-instance-types).
  
* RAM Usage
  
  Launching Clocker 2 within some environments may require RAM to be made available. This can be set to more than `2G` by 
  editing the following:
  
  * In Brooklyn classic edit `bin/brooklyn` and change the line `JAVA_OPTS="-Xms256m -Xmx2g -XX:MaxPermSize=256m"`.
  
  * In Brooklyn Karaf edit `bin/setenv` and edit or add the line `export JAVA_MAX_MEM="2G"` to the start.
  
* Failed to find machine-unique group on node
  
  This is caused because of a restrictive security group within your cloud. Add a new piece of config to your swarm or cluster 
  in the form `kubernetes.create.shared.securitygroup` or `swarm.create.shared.securitygroup` and set this to `false`.
  
  Then create a new security group in your cloud and specify this in your location using the [securityGroups](http://brooklyn.apache.org/v/latest/ops/locations/index.html#vm-creation) key.
  
  For more information on setting up a location, see the getting started section.
