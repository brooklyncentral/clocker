---
layout: base
title: Swarm Cluster Tutorial
---

### Introduction
This tutorial is focused on deploying a production ready Docker Swarm.

### Pre-requisites
This tutorial assumes you have completed the [getting started](getting-started.html) section of this website and have installed the [Apache Brooklyn CLI](https://brooklyn.apache.org/v/latest/ops/cli/index.html).

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
Firstly, we need to setup a location to deploy the Swarm cluster to. We recommend the following settings:

- use the `installDevUrandom` config to prevent installation speed being slowed by lack of entropy. See [Entropy Troubleshooting](https://brooklyn.apache.org/v/latest/ops/troubleshooting/increase-entropy.html)
- use at least 2GB RAM
- use a CentOS 7 based image

Please note that we recommend the [official Centos 7 images](https://wiki.centos.org/Cloud/AWS). Images from other providers may be less functional or incompatible.

The following catalog items should enable you to quickly get started on some popular clouds. Download the `.bom` file of the relevant cloud, add your credentials, and then run:

```bash
br add-catalog <CLOUD-PROVIDER>-example-location.bom
```

{% tabs aws='AWS', sl='SoftLayer', azure='Azure', gce='GCE', bb='Blue Box' %}

{% tab id='aws', class='active' %}
```yaml
{% include_relative locations/aws-example-location.bom %}
```
[Download aws-example-location.bom](locations/aws-example-location.bom){:target="blank" class="button download"}
{% endtab %}

{% tab id='sl' %}
```yaml
{% include_relative locations/sl-example-location.bom %}
```
[Download sl-example-location.bom](locations/sl-example-location.bom){:target="blank" class="button download"}
{% endtab %}

{% tab id='azure' %}
```yaml
{% include_relative locations/azure-example-location.bom %}
```
[Download azure-example-location.bom](locations/azure-example-location.bom){:target="blank" class="button download"}
{% endtab %}

{% tab id='gce' %}
```yaml
{% include_relative locations/gce-example-location.bom %}
```
[Download gce-example-location.bom](locations/gce-example-location.bom){:target="blank" class="button download"}
{% endtab %}

{% tab id='bb' %}
```yaml
{% include_relative locations/bb-example-location.bom %}
```
[Download bb-example-location.bom](locations/bb-example-location.bom){:target="blank" class="button download"}
{% endtab %}

{% endtabs %}

#### Deploy a Swarm Cluster
After the location is setup, it is time to deploy a Docker Swarm.

{% tabs amp='AMP', brooklyn='Brooklyn' %}

{% tab id='amp', class='active' %}
From your AMP Install, head to the AMP Welcome page. In the quick deploy section select "Docker Swarm with Discovery and CA" and select the location that that we setup in the previous step. You can also change some configuration options such as the minimum and maximum number of nodes. Once you are happy with the configuration press Deploy and your Swarm cluster will be created.
{% endtab %}

{% tab id='brooklyn' %}
From your Brooklyn Install, head to the Home tab. Click on "Add application" and select "Docker Swarm with Discovery and CA", then click on "Next". Select the location that that we setup in the previous step. You can also change some configuration options such as the minimum and maximum number of nodes. Once you are happy with the configuration, press "Deploy" and your Swarm cluster will be created.
{% endtab %}

{% endtabs %}

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
