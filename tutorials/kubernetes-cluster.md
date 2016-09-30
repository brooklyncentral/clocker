---
layout: base
title: Kubernetes Cluster Tutorial
---

### Introduction
This tutorial is focused on deploying a production ready Kubernetes cluster.

### Overview
The production ready Kubernetes cluster is comprised of the following components:

### Pre-requisites
This tutorial assumes you have [installed](https://brooklyn.apache.org/v/latest/start/running.html) Apache Brooklyn, and the [Apache Brooklyn CLI](https://brooklyn.apache.org/v/latest/ops/cli/index.html).

#### Kubernetes cluster
This Kubernetes cluster contains a manager and a configurable number of workers.
It requires a pre-existing discovery mechanism and references to a CA server entity.
The cluster has an AutoScalerPolicy and will scale up due to high CPU usage. It also has a replacer policy that will detect the failure and replace the failed worker.

#### etcd Cluster
Used as a discovery backend for the Kubernetes cluster.

#### CA Server
This is used to provide TLS certificates for the Kubernetes cluster. This component is designed to be easily replaced. It is strongly recommended that this component is replaced with a production grade CA server of your choice.

### Instructions

#### Setup a cloud location
Firstly, we need to setup a location to deploy the Kubernetes cluster to. We recommend the following settings:

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

#### Deploy a Kubernetes Cluster
After the location is setup, it is time to deploy a Kubernetes cluster. From your Brooklyn Install, head to the Home tab. Click on "Add application" and select "Kubernetes cluster with a master node and worker nodes", then click on "Next". Select the location that that we setup in the previous step. You can also change some configuration options such as the minimum and maximum number of nodes. Once you are happy with the configuration, press "Deploy" and your Kubernetes cluster will be created.

To interact with the Kubernetes cluster, log in into the Kubernetes Dashboard (URL will be available as "main uri" sensor into the brooklyn console)

### What's next?
Jump into the [documentation]({{site.baseurl}}/docs/kubernetes-cluster.html) to learn more about kubernetes support in Clocker and have an in-depth overview.