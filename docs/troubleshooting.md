---
layout: base
title: Troubleshooting
---

### General

You can find below the most common issues (and their solutions) you may face when deploy entities provided by Clocker. For more general troubleshooting, please check out the general [troubleshooting](https://brooklyn.apache.org/v/latest/ops/troubleshooting/index.html) or [AMP](http://docs.cloudsoft.io/operations/troubleshooting/) docs.

#### Persisted State

If you have previously run AMP or Brooklyn you may have to remove the persisted state these have saved on your machine.
To do this, delete the folder `~/.brooklyn/brooklyn-persisted-state/` and all of it's contents.

#### AWS VPC / Classic
  
It's recommended that you use [VPC mode](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-vpc.html) to run Clocker within [AWS](http://aws.amazon.com/). This is because problems have been recorded with DNS and starting instances withn AWS Classic. For more information on doing this, see the [Brooklyn docs](http://brooklyn.apache.org/v/latest/ops/locations/index.html#ec2-classic-problems-with-vpc-only-hardware-instance-types).

### AWS 401 errors

If you are getting 401 response errors when trying to provision machines make sure that:
  * The credentials you are using have the permission to create instances
  * You have accepted the TOC for the image you are using in the location definition.
    Go to https://aws.amazon.com/marketplace/pp/B00O7WM7QW and proceed to launch an instance with that image.
    In the process you'll be asked to accept the TOC. You can abort the launch at this point.
  
#### High CPU usage

After a few hours of management of a swarm or cluster, AMP or Brooklyn may start to suffer from high CPU and memory usage. This is because of a known issue in the Java garbage collection system. To fix this add the following to your AMP or Brooklyn: 

{% tabs cpuclassic='Brooklyn classic', cpukaraf='AMP / Brooklyn Karaf' %}

{% tab id='cpuclassic', class='active' %}
Open `bin/brooklyn` and add `-XX:SoftRefLRUPolicyMSPerMB=1` to the `JAVA_OPTS` line:

```sh
JAVA_OPTS="-Xms256m -Xmx2g -XX:MaxPermSize=256m -XX:SoftRefLRUPolicyMSPerMB=1"
```
{% endtab %}

{% tab id='cpukaraf' %}
Open `bin/setenv` and edit or add the following line to the start

```sh
export EXTRA_JAVA_OPTS="-XX:SoftRefLRUPolicyMSPerMB=1 ${EXTRA_JAVA_OPTS}"
``` 
{% endtab %}

{% endtabs %}
  
#### RAM Usage
  
Launching Clocker within some environments may require RAM to be made available. This can be set to more than `2G` by editing the following:

{% tabs ramclassic='Brooklyn classic', ramkaraf='AMP / Brooklyn Karaf' %}

{% tab id='ramclassic', class='active' %}
Open `bin/brooklyn` and change the line

```sh
JAVA_OPTS="-Xms256m -Xmx2g -XX:MaxPermSize=256m"
```
{% endtab %}

{% tab id='ramkaraf' %}
Open `bin/setenv` and edit or add the line to the start

```sh
export JAVA_MAX_MEM="2G"
```
{% endtab %}

{% endtabs %}
  
#### Failed to find machine-unique group on node
  
This is caused because of a restrictive security group within your cloud. Add a new piece of config to your swarm or cluster in the form `kubernetes.sharedsecuritygroup.create` or `swarm.sharedsecuritygroup.create` and set this to `false`.
  
Then create a new security group in your cloud and specify this in your location using the [securityGroups key](http://brooklyn.apache.org/v/latest/ops/locations/index.html#vm-creation).
  
For more information on setting up a location, see the [Getting Started](../index.html#getting-started) guide.
