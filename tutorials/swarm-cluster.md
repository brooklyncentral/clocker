---
layout: base
title: Swarm Cluster Tutorial
---

### Introduction
This tutorial is focused on deploying a production ready swarm cluster.

### Pre-requisites
This tutorial assumes you have [installed](https://brooklyn.apache.org/v/latest/start/running.html) Apache Brooklyn, and the [Apache Brooklyn CLI](https://brooklyn.apache.org/v/latest/ops/cli/index.html).

### Overview
The production ready swarm cluster is comprised of the following components:

#### A load-balanced cluster of swarm managers
Swarm managers control a swarm's nodes and dictate the node on which containers are deployed.
We interact directly with the swarm manager cluster's load balancer as if it were a single docker node.
The load-balancer will redirect traffic to a healthy manager when a manager fails.  The replacer policy will detect the failure and replace the failed manager.

#### A cluster of swarm nodes
These nodes are where docker containers are deployed to. The cluster has an AutoScalerPolicy and will scale up due to high CPU usage.

#### etcd Cluster
Used as a discovery backend for the swarm cluster.

#### CA Server
This is used to provide TLS certificates for the swarm cluster. This component is designed to be easily replaced. It is strongly recommended that this component is replaced with a production grade CA server of your choice.

### Instructions

#### Setup a cloud location
Firstly, we need to setup a location to deploy the Swarm cluster to.  We recommend the following settings:

- use the `installDevUrandom` config to prevent installation speed being slowed by lack of entropy. See [Entropy Troubleshooting](https://brooklyn.apache.org/documentation/increase-entropy.html)
- use at least 2GB RAM
- use a CentOS 7 based image

The following catalog items should enable you to quickly get started on some popular clouds. Download the .bom file of the relevant cloud, add your credentials, and then run:

    br add-catalog <CLOUD-PROVIDER>-example-location.bom

{::options parse_block_html="true" /}

<ul class="nav nav-tabs">
    <li class="active impl-1-tab"><a data-target="#impl-1, .impl-1-tab" data-toggle="tab" href="#">AWS</a></li>
    <li class="impl-2-tab"><a data-target="#impl-2, .impl-2-tab" data-toggle="tab" href="#">SoftLayer</a></li>
    <li class="impl-3-tab"><a data-target="#impl-3, .impl-3-tab" data-toggle="tab" href="#">Azure</a></li>
    <li class="impl-4-tab"><a data-target="#impl-4, .impl-4-tab" data-toggle="tab" href="#">GCE</a></li>
    <li class="impl-5-tab"><a data-target="#impl-5, .impl-5-tab" data-toggle="tab" href="#">Blue Box</a></li>
</ul>

<div class="tab-content">
<div id="impl-1" class="tab-pane fade in active">
{% highlight yaml %}
{% include_relative locations/aws-example-location.bom %}
{% endhighlight %}
[Download aws-example-location.bom](locations/aws-example-location.bom){:target="blank" class="button download"}
</div>
<div id="impl-2" class="tab-pane fade">
{% highlight yaml %}
{% include_relative locations/sl-example-location.bom %}
{% endhighlight %}
[Download sl-example-location.bom](locations/sl-example-location.bom){:target="blank" class="button download"}
</div>
<div id="impl-3" class="tab-pane fade">
{% highlight yaml %}
{% include_relative locations/azure-example-location.bom %}
{% endhighlight %}
[Download azure-example-location.bom](locations/azure-example-location.bom){:target="blank" class="button download"}
</div>
<div id="impl-4" class="tab-pane fade">
{% highlight yaml %}
{% include_relative locations/gce-example-location.bom %}
{% endhighlight %}
[Download gce-example-location.bom](locations/gce-example-location.bom){:target="blank" class="button download"}
</div>
<div id="impl-5" class="tab-pane fade">
{% highlight yaml %}
{% include_relative locations/bb-example-location.bom %}
{% endhighlight %}
[Download bb-example-location.bom](locations/bb-example-location.bom){:target="blank" class="button download"}
</div>
</div>

#### Deploy a Swarm Cluster
After the location is setup, it is time to deploy a Swarm cluster. From your Brooklyn Install, head to the Home tab. Click on "Add application" and select "Docker Swarm with Discovery and CA", then click on "Next". Select the location that that we setup in the previous step. You can also change some configuration options such as the minimum and maximum number of nodes. Once you are happy with the configuration, press "Deploy" and your Swarm cluster will be created.

To interact with the Swarm cluster, we first need to get certificates from the CA server. To do so, the following [script](getcert.sh){:target="blank"} can be used:
{% highlight bash %}
{% include_relative getcert.sh %}
{% endhighlight %}

To communicate with the cluster, you must communicate directly with the Swarm master. To do so, first retrieve the Swarm master URI and port. This can be found by checking for the "host.name" and "swarm.port" sensor. After, ensure you have the Docker CLI installed then set up the following environment variables:
{% highlight bash %}
export DOCKER_HOST=tcp://<Swarm Master URI & port>
export DOCKER_TLS_VERIFY=true
export DOCKER_CERT_PATH=<CERT_DIR>
{% endhighlight %}

You will now be able to run Docker commands against the Swarm cluster.

### What's next?
Jump into the [documentation]({{site.baseurl}}/docs/swarm-cluster.html) to learn more about Docker Swarm support in Clocker and have an in-depth overview.