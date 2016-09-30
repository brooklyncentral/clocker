---
layout: base
title: Swarm Location Tutorial
---

### Introduction
This tutorial is focused on deploying apps to a Swarm cluster. Please note, this can be any swarm cluster, not just a cluster provisioned by AMP. However for the purpose of this tutorial we will assume that you provisioned the cluster using the approach described in the [Swarm Cluster Tutorial](swarm-cluster.html)

### Pre-requisites

This tutorial assumes you have [installed](https://brooklyn.apache.org/v/latest/start/running.html) Apache Brooklyn, and the [Apache Brooklyn CLI](https://brooklyn.apache.org/v/latest/ops/cli/index.html). It also assumes you have followed the [steps](swarm-cluster.html) to get a swarm cluster running.

### Instructions

#### Creating an AMP location
To deploy to a swarm cluster we must represent the cluster as an AMP location. We will do this by creating a swarm location in our amp catalog with the following [yaml](locations/swarm-example-location.bom){:target="blank"}:

{% highlight YAML %}
{% include_relative locations/swarm-example-location.bom %}
{% endhighlight %}

The hostname and port can be retrieved from the `swarm.endpoint` sensor on the swarm entity. The following command will retrieve this info:

    br app swarm entity swarm-manager-load-balancer sensor swarm.endpoint

You should use the cert.pem and key.pem created in the [Swarm Cluster Tutorial](swarm-cluster.html).

Once you have added these values to the bom file you can add it to you catalog with this command:

    br add-catalog swarm-example-location.bom

#### Deploying to our swarm cluster

The location we just created can be used, as you would use any other cloud location. The [yaml](multi-node-application.bom){:target="blank"} below deploys a three tier web app to your swarm location.

{% highlight YAML %}
{% include_relative multi-node-application.bom %}
{% endhighlight %}

You can deploy it with the following command:

    br deploy multi-node-application.bom

Once it has been deployed you can get the publicly available endpoint using the following command:

    br app "3-Tier web app on swarm" entity "Load Balancer (nginx)" sensor main.uri.mapped.public

If you inspect the above yaml it is similar to the yaml discussed in previous tutorials e.g. [Policy Tutorial](/tutorials/policies_intro.html) except we have included a separate mysql database. The main difference is the use of the `OnPublicNetworkEnricher`. We use this to indicate to AMP any ports that need to be available outside the swarm network.  In this case we are making the load balancer available so that we can use our deployed app. We might also use this if we were deploying an application on a mix of containers and virtual machines.
