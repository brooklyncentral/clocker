---
layout: base
title: Kubernetes Cluster Tutorial
---

### Introduction
This tutorial is focused on deploying a production ready Kubernetes cluster.

### Overview
The production ready Kubernetes cluster is comprised of the following components:

### Pre-requisites
This tutorial assumes you have completed the [getting started](getting-started.html) section of this website and have installed the [Apache Brooklyn CLI](https://brooklyn.apache.org/v/latest/ops/cli/index.html).

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

- use at least 2GB RAM
- use a CentOS 7 based image

Please note that we recommend the official Centos 7 images ([AWS](https://wiki.centos.org/Cloud/AWS), [OpenStack](http://cloud.centos.org/centos/7/images/)). Images from other providers may be less functional or incompatible.
For Amazon make sure you've accepted the Marketplace Terms and Conditions for the image before using it.

The following catalog items should enable you to quickly get started on some popular clouds. Download the .bom file of the relevant cloud, add your credentials, and then run:

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

#### Deploy a Kubernetes Cluster
After the location is setup, it is time to deploy a Kubernetes cluster.

{% tabs amp='AMP', brooklyn='Brooklyn' %}

{% tab id='amp', class='active' %}
From your AMP Install, head to the AMP Welcome page. In the quick deploy section select "Kubernetes cluster with a master node and worker nodes" and select the location that that we setup in the previous step Select the location that that we setup in the previous step. You can also change some configuration options such as the minimum and maximum number of nodes. Once you are happy with the configuration, press "Deploy" and your Kubernetes cluster will be created.

See the location example for [IBM BlueBox](locations/bb-example-location.bom) and [AWS](locations/aws-example-location.bom) for extra config that may be required.
{% endtab %}

{% tab id='brooklyn' %}
From your Brooklyn Install, head to the Home tab. Click on "Add application" and select "Kubernetes cluster with a master node and worker nodes", then click on "Next". Select the location that that we setup in the previous step. You can also change some configuration options such as the minimum and maximum number of nodes. Once you are happy with the configuration, press "Deploy" and your Kubernetes cluster will be created.

See the location example for [IBM BlueBox](locations/bb-example-location.bom) and [AWS](locations/aws-example-location.bom) for extra config that may be required.
{% endtab %}

{% endtabs %}

To interact with the Kubernetes cluster, log in into the Kubernetes Dashboard (URL will be available as "main uri" sensor into the brooklyn console)

### What's next?
Jump into the [documentation]({{site.baseurl}}/docs/kubernetes-cluster.html) to learn more about kubernetes support in Clocker and have an in-depth overview.
