---
layout: base
title: Home
---

## What is it?
Clocker for [Apache Brooklyn](https://brooklyn.apache.org/) is a set of open source, [Apache Licensed](https://www.apache.org/licenses/LICENSE-2.0) tools designed to make working with [Docker](https://www.docker.com/) containers as simple as a few clicks. Clocker contains [Brooklyn blueprints](http://brooklyn.apache.org/v/latest/start/blueprints.html) to enable deployment and management of [Docker Swarms](https://www.docker.com/products/docker-swarm) and [Kubernetes clusters](http://kubernetes.io/).

## What can you do with it?

* You can easily deploy production grade Docker Swarms or Kubernetes clusters to a [range of clouds](http://brooklyn.apache.org/v/latest/ops/locations/index.html) (including [AWS](https://aws.amazon.com/), [Azure](https://azure.microsoft.com), [Google Cloud](https://cloud.google.com/), [IBM Softlayer](http://www.softlayer.com/), and [IBM BlueBox](https://www.blueboxcloud.com/).
* You can manage and scale Swarms or clusters in real time.

## Getting started

1. Firstly, you need to have Apache Brooklyn installed and running, if you have not already done this, please [follow these instructions](https://brooklyn.apache.org/v/latest/start/running.html).
2. Once this is done, [download the `clocker.bom` file](clocker.bom). It contains all entities required to run containerised solutions. You need to add this file to your [Brooklyn catalog](http://brooklyn.apache.org/v/latest/ops/catalog/index.html). You can achieve this by either drag-and-dropping the file into the YAML editor (composer tab of the Brooklyn UI) or by using the [Brooklyn CLI](https://brooklyn.apache.org/v/latest/ops/cli/index.html) as follows:

   ```sh
   br add-catalog clocker.bom
   ```
3. You will then need to [setup a location](https://brooklyn.apache.org/v/latest/ops/locations/index.html) to which you will deploy these entities. We came up with location's templates to add to your catalog, that you can use out of the box for [AWS](tutorial/locations/aws-example-location.bom), [SoftLayer]((tutorial/locations/sl-example-location.bom)), [Azure](tutorial/locations/azure-example-location.bom), [GCE](tutorial/locations/gce-example-location.bom) and [Blue Box](tutorial/locations/bb-example-location.bom). You can find more information about what is going on in those files [here](tutorial/swarm-cluster.html#setup-a-cloud-location).
4. Job done, you should now be all set!

The last thing to do is to deploy one (or more) of these new entities. They should be available as an application in the "Create Application" dialog. Check out [our tutorials](tutorials) or you use our [yaml examples](examples/swarm.yaml){:target="blank"} to get started.
