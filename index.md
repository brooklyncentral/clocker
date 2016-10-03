---
layout: base
title: Home
---

## What is it?
Clocker for [Apache Brooklyn](https://brooklyn.apache.org/) is a set of open source, [Apache Licensed](https://www.apache.org/licenses/LICENSE-2.0) tools designed to make working with [Docker](https://www.docker.com/) containers as simple as a few clicks. Clocker contains [Brooklyn blueprints](http://brooklyn.apache.org/v/latest/start/blueprints.html) to enable deployment and management of [Docker Swarms](https://www.docker.com/products/docker-swarm) and [Kubernetes clusters](http://kubernetes.io/).

## What can you do with it?

* You can easily deploy production grade Docker Swarms or Kubernetes clusters to a [range of clouds](http://brooklyn.apache.org/v/latest/ops/locations/index.html) (including [AWS](https://aws.amazon.com/), [Azure](https://azure.microsoft.com), [Google Cloud](https://cloud.google.com/), [IBM Softlayer](http://www.softlayer.com/), and [IBM BlueBox](https://www.blueboxcloud.com/)).
* You can manage and scale Swarms or clusters in real time.

## Getting started
To use these blueprints you have two options. You can build the open source [Apache Brooklyn](http://brooklyn.apache.org/) and add the BOM files manually. Alternatively you can use a pre-packed application management platform [Cloudsoft AMP](http://www.cloudsoft.io/products/) which is built on the foundation of Brooklyn plus additional features such as a rich UX, blueprint QA and commercial support available from Cloudsoft.

### Apache Brooklyn

1. Firstly, you need to have a build of Apache Brooklyn, if you have not already done this, please [follow these instructions](https://brooklyn.apache.org/v/latest/dev/env/maven-build.html) to build Brooklyn.
2. You will need to manually build [brooklyn-etcd](https://github.com/brooklyncentral/brooklyn-etcd/) and [Clocker](https://github.com/brooklyncentral/clocker). You can do so by running `mvn clean install` for these 2 projects
3. Copy the brooklyn-etcd, common, swarm and kubernetes jar into 'lib/dropins' in your Brooklyn directory
4. Launch Brooklyn using [these instructions](https://brooklyn.apache.org/v/latest/start/running.html)
5. [Download the `clocker.bom` file](clocker.bom). It contains all entities required to run containerised solutions. You need to add this file to your [Brooklyn catalog](http://brooklyn.apache.org/v/latest/ops/catalog/index.html). You can achieve this by either drag-and-dropping the file into the YAML editor (composer tab of the Brooklyn UI) or by using the [Brooklyn CLI](https://brooklyn.apache.org/v/latest/ops/cli/index.html) as follows:

   ```sh
   br add-catalog clocker.bom
   ```
6. You will then need to [set up a location](https://brooklyn.apache.org/v/latest/ops/locations/index.html) to which you will deploy these entities. We came up with location's templates to add to your catalog, that you can use out of the box for [AWS](tutorials/locations/aws-example-location.bom), [SoftLayer]((tutorials/locations/sl-example-location.bom)), [Azure](tutorials/locations/azure-example-location.bom), [GCE](tutorials/locations/gce-example-location.bom) and [Blue Box](tutorials/locations/bb-example-location.bom). You can find more information about what is going on in those files [here](tutorials/swarm-cluster.html#setup-a-cloud-location).
7. The last thing to do is to deploy one (or more) of these new entities. They should be available as an application in the "Create Application" dialog. 

### Cloudsoft AMP
Cloudsoft AMP makes it easy to consume Brooklyn and adds a rich UX, Blueprint QA along with Cloudsoft support. You can use the full AMP  as a free trial with no time limit for non-production use. A version of AMP which includes the open-sourced container service blueprints can be found [here](http://download.cloudsoft.io/amp/4.1.0-20160930.1659/cloudsoft-amp-karaf-4.1.0-20160930.1659.tar.gz).

If you are interested in exploring AMP further please visit [this website](http://www.cloudsoft.io/).

1. Please see the [docs](http://docs.cloudsoft.io/tutorials/tutorial-get-amp-running.html) to get AMP running. As we have provided a tarball, please see the "OSX / DIY" section.
2. As in step 6 above, please add locations to your catalog. You can use the templates from step 6. You can find more information about adding locations to AMP [here](http://docs.cloudsoft.io/locations/index.html).
3. From the quick launch select either "Docker Swarm with Discovery and CA" or "Kubernetes Cluster".
4. Select the location you set up in step 2 and hit deploy.

### Tutorials and Example
Check out [our tutorials](tutorials) or use our [yaml examples](examples/swarm.yaml){:target="blank"} for more information.

### Troubleshooting
For any problems please consult the [troubleshooting section here](./docs/troubleshooting.html).
