---
layout: base
title: Home
children:
- docs/index.md
- tutorials/index.md
---

## What is it?
Clocker for [Apache Brooklyn](https://brooklyn.apache.org/) is a set of open source, [Apache Licensed](https://www.apache.org/licenses/LICENSE-2.0) tools designed to make working with [Docker](https://www.docker.com/) containers as simple as a few clicks. Clocker contains [Brooklyn blueprints](http://brooklyn.apache.org/v/latest/start/blueprints.html) to enable deployment and management of [Docker Swarms](https://www.docker.com/products/docker-swarm) and [Kubernetes clusters](http://kubernetes.io/).

## What can you do with it?

* You can easily deploy production grade Docker Swarms or Kubernetes clusters to a [range of clouds](http://brooklyn.apache.org/v/latest/ops/locations/index.html) (including [AWS](https://aws.amazon.com/), [Azure](https://azure.microsoft.com), [Google Cloud](https://cloud.google.com/), [IBM Softlayer](http://www.softlayer.com/), and [IBM BlueBox](https://www.blueboxcloud.com/)).
* You can manage and scale Swarms or clusters in real time.

## Getting started
*The Clocker blueprints run in Apache Brooklyn. These instructions assume some familiarity with Apache Brooklyn. Appropriate links are included for those new to Brooklyn. For background information, see [The Theory Behind Brooklyn](http://brooklyn.apache.org/learnmore/theory.html) and the [Brooklyn Getting Started guide](http://brooklyn.apache.org/v/latest/start/).*

To use the Clocker blueprints you have two options:

* use them pre-packaged in Cloudsoft AMP (which is built on Brooklyn with additional enterprise features such as a rich UX, blueprint QA, and commercial support). This gives the simplest and fastest user-experience.
* use Apache Brooklyn, manually adding the Clocker blueprints.

{::options parse_block_html="true" /}

<ul class="nav nav-tabs">
    <li class="active amp-tab"><a data-target="#amp, .amp-tab" data-toggle="tab" href="#">AMP</a></li>
    <li class="brooklyn-tab"><a data-target="#brooklyn, .brooklyn-tab" data-toggle="tab" href="#">Brooklyn</a></li>
</ul>

<div class="tab-content">
<div id="amp" class="tab-pane fade in active">

1. Download the appropriate AMP installer:
 * [RPM](http://download.cloudsoft.io/amp/4.1.0-20160930.1659/cloudsoft-amp-karaf-4.1.0-20160930.1659-noarch.rpm) 
 * [DEB](http://download.cloudsoft.io/amp/4.1.0-20160930.1659/cloudsoft-amp-karaf-4.1.0-20160930.1659-all.deb)
 * [tar ball](http://download.cloudsoft.io/amp/4.1.0-20160930.1659/cloudsoft-amp-karaf-4.1.0-20160930.1659.tar.gz)
2. Install AMP. For more detailed instructions, see the [docs](http://docs.cloudsoft.io/tutorials/tutorial-get-amp-running.html#install-cloudsoft-amp).
3. Open the AMP UI in your favourite web browser (as per the detailed instructions within the link above) and [setup a location](http://docs.cloudsoft.io/tutorials/tutorial-get-amp-running.html#add-a-location) for where you want to deploy to (e.g. your preferred cloud, or a list of IPs for pre-existing machines). We came up with location's templates to add to your catalog, that you can use out of the box for [AWS](tutorials/locations/aws-example-location.bom), [SoftLayer]((tutorials/locations/sl-example-location.bom)), [Azure](tutorials/locations/azure-example-location.bom), [GCE](tutorials/locations/gce-example-location.bom) and [Blue Box](tutorials/locations/bb-example-location.bom). For more information, head to the [tutorial section](tutorials/swarm-cluster.html#setup-a-cloud-location).
4. AMP includes a set of quick launch applications, for point-and-click deployment to your favourite location. These include `Docker Swarm with Discovery and CA` and `Kubernetes Cluster`. Choose the desired application, then your location, and any custom configuration options such as the size of cluster. Then click Deploy.
5. View your application in the App Dashboard (for a high-level overview) or the App Inspector (for a more detailed view). Once the app is deployed, this will show important information such as the connection details.

</div>
<div id="brooklyn" class="tab-pane fade">

*Clocker relies on some recent Brooklyn features that will be available in the next **0.10.0 release**. Before that release is available, you can either use a [pre-built early access release of Brooklyn](http://download.cloudsoft.io/brooklyn/0.10.0-20160930.1659/brooklyn-dist-0.10.0-20160930.1659-dist.tar.gz) or you can [download the code](http://brooklyn.apache.org/developers/code/) and [build Brooklyn master from source](http://brooklyn.apache.org/v/latest/dev/env/maven-build.html).*

The instructions below assume you are using the Brooklyn in the "classic mode" (i.e. not using Karaf). They pick up from where you have the `.tgz` or `.zip` file).

1. Install Apache Brooklyn. For more detailed instructions, see the [docs](http://brooklyn.apache.org/v/latest/start/running.html#install-apache-brooklyn).
2. Download the required Clocker files, and add them to the Brooklyn `./lib/dropins/` folder (though these are jar files, the Clocker jars just package resources such as YAML files rather than Java code):
 * [brooklyn-etcd](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT)
 * [common](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=common&v=2.0.0)
 * [swarm](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=swarm&v=2.0.0)
 * [kubernetes](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=kubernetes&v=2.0.0)
3. [Launch Brooklyn](https://brooklyn.apache.org/v/latest/start/running.html) and add the Clocker blueprints to the catalog by using the [`clocker.bom` file](clocker.bom) that lists the catalog items:
 * If you'd like to use the command line to do this, you will need the [Apache Brooklyn CLI](https://brooklyn.apache.org/v/latest/ops/cli/index.html) and run `br add-catalog clocker.bom`. For more details, see the [docs](http://brooklyn.apache.org/v/latest/ops/cli/index.html).
 * Alternatively, if you'd like to use the web-console, choose the "Composer" tab, click the "Catalog" button, paste the contents of the `clocker.bom` file into the online editor, and click "Deploy". For more details, see the [docs](http://brooklyn.apache.org/v/latest/ops/catalog/).
4. [Setup a location](http://brooklyn.apache.org/v/latest/ops/locations/) for where you want to deploy to. This can be done by defining your location configuration in a .bom file and deploying using the `br` CLI. Alternatively, the web-console can be used: click on the Catalog tag, then the "+" button", choose "Location", and follow the instructions in the wizard. We came up with location's templates to add to your catalog, that you can use out of the box for [AWS](tutorials/locations/aws-example-location.bom), [SoftLayer]((tutorials/locations/sl-example-location.bom)), [Azure](tutorials/locations/azure-example-location.bom), [GCE](tutorials/locations/gce-example-location.bom) and [Blue Box](tutorials/locations/bb-example-location.bom). For more information, head to the [tutorial section](tutorials/swarm-cluster.html#setup-a-cloud-location).
5. The catalog items added previously will be available in the Brooklyn quick launch. In the web-console, from the Home tab click the "Add application" button, choose your application, then your location, and any custom configuration options such as the size of cluster. Then click Deploy. For more details, see the [docs](http://brooklyn.apache.org/v/latest/ops/gui/blueprints.html#launching-from-the-catalog).
6. View your application in the "Applications" tab. Once the app is deployed, this will show important information such as the connection details. Click on the entity (i.e. component) in the tree view, and the "Sensors" tab to see details of that entity. For more details, the [docs](see http://brooklyn.apache.org/v/latest/ops/gui/managing.html).

</div>
</div>

## Tutorials and Example
Check out [our tutorials](tutorials) or you use our [yaml examples](examples/swarm.yaml){:target="blank"} for more information.

## Troubleshooting
For any problems please consult the [troubleshooting section here](./docs/troubleshooting.html).
