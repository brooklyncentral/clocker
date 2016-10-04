---
layout: base
title: Troubleshooting
---

### General

You can find below the most common issues (and their solutions) you may face when deploy entities provided by Clocker. For more general troubleshooting, please check out the [Brooklyn](https://brooklyn.apache.org/v/latest/ops/troubleshooting/index.html) or [AMP](http://docs.cloudsoft.io/operations/troubleshooting/) docs.

#### Persisted State

If you have previously run AMP or Brooklyn you may have to remove the persisted state these have saved on your machine.
To do this, delete the folder `~/.brooklyn/brooklyn-persisted-state/` and all of it's contents.

#### AWS VPC / Classic
  
It's recommended that you use [VPC mode](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-vpc.html) to run Clocker within [AWS](http://aws.amazon.com/). This is because problems have been recorded with DNS and starting instances withn AWS Classic. For more information on doing this, see the [Brooklyn docs](http://brooklyn.apache.org/v/latest/ops/locations/index.html#ec2-classic-problems-with-vpc-only-hardware-instance-types).
  
#### RAM Usage
  
Launching Clocker within some environments may require RAM to be made available. This can be set to more than `2G` by editing the following:

{::options parse_block_html="true" /}

<ul class="nav nav-tabs">
    <li class="active classic-tab"><a data-target="#classic, .classic-tab" data-toggle="tab" href="#">Brooklyn classic</a></li>
    <li class="karaf-tab"><a data-target="#karaf, .karaf-tab" data-toggle="tab" href="#">Brooklyn Karaf</a></li>
</ul>

<div class="tab-content">
<div id="classic" class="tab-pane fade in active">

Open `bin/brooklyn` and change the line

```sh
JAVA_OPTS="-Xms256m -Xmx2g -XX:MaxPermSize=256m"
```

</div>
<div id="karaf" class="tab-pane fade">

Open `bin/setenv` and edit or add the line to the start

```sh
export JAVA_MAX_MEM="2G"
``` 

</div>
</div>
  
#### Failed to find machine-unique group on node
  
This is caused because of a restrictive security group within your cloud. Add a new piece of config to your swarm or cluster in the form `kubernetes.sharedsecuritygroup.create` or `swarm.sharedsecuritygroup.create` and set this to `false`.
  
Then create a new security group in your cloud and specify this in your location using the [securityGroups key](http://brooklyn.apache.org/v/latest/ops/locations/index.html#vm-creation).
  
For more information on setting up a location, see the [Getting Started](../index.html#getting-started) guide.
