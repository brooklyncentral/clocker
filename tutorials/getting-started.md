---
layout: base
title: Getting Started guide
---

### Overview

*The Clocker blueprints run in Apache Brooklyn. These instructions assume some familiarity with Apache Brooklyn. Appropriate links are included for those new to Brooklyn. For background information, see [The Theory Behind Brooklyn](http://brooklyn.apache.org/learnmore/theory.html) and the [Brooklyn Getting Started guide](http://brooklyn.apache.org/v/latest/start/).*

To use the Clocker blueprints you have two options:

* use them pre-packaged in Cloudsoft AMP (which is built on Brooklyn with additional enterprise features such as a rich UX, blueprint QA, and commercial support). This gives the simplest and fastest user-experience.
* use Apache Brooklyn, manually adding the Clocker blueprints.

{% tabs amp='AMP', brooklyn='Brooklyn' %}

{% tab id='amp', class='active' %}
1. [Download AMP](http://www.cloudsoft.io/amp-container-service-early-access)
2. Install AMP. For more detailed instructions, see the [docs](http://docs.cloudsoft.io/tutorials/tutorial-get-amp-running.html#install-cloudsoft-amp).
3. Open the AMP UI in your favourite web browser (as per the detailed instructions within the link above) and [setup a location](http://docs.cloudsoft.io/tutorials/tutorial-get-amp-running.html#add-a-location) for where you want to deploy to (e.g. your preferred cloud, or a list of IPs for pre-existing machines). We came up with location's templates to add to your catalog, that you can use out of the box for [AWS](locations/aws-example-location.bom), [SoftLayer](locations/sl-example-location.bom), [Azure](locations/azure-example-location.bom), [GCE](locations/gce-example-location.bom) and [Blue Box](locations/bb-example-location.bom). For more information, head to the [tutorial section](swarm-cluster.html#setup-a-cloud-location).

   *Please note that we recommend the [official Centos 7 images](https://wiki.centos.org/Cloud/AWS). Images from other providers may be less functional or incompatible.*

4. AMP includes a set of quick launch applications, for point-and-click deployment to your favourite location. These include `Docker Swarm with Discovery and CA` and `Kubernetes Cluster`. Choose the desired application, then your location, and any custom configuration options such as the size of cluster. Then click Deploy.
  ![Quick-launch of a Kubernetes cluster]({{site.baseurl}}/assets/images/quick-launch-amp-kubernetes.png)
5. View your application in the App Dashboard (for a high-level overview) or the App Inspector (for a more detailed view). Once the app is deployed, this will show important information such as the connection details.
{% endtab %}

{% tab id='brooklyn' %}
The instructions below assume you are using the Brooklyn in the "classic mode" (i.e. not using Karaf). They pick up from where you have the `.tgz` or `.zip` file).

1. Install Apache Brooklyn. For more detailed instructions, see the [docs](http://brooklyn.apache.org/v/latest/start/running.html#install-apache-brooklyn).
2. Download the required Clocker files, and add them to the Brooklyn `./lib/dropins/` folder (though these are jar files, the Clocker jars just package resources such as YAML files rather than Java code):
 * [brooklyn-etcd](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.4.0)
 * [common](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=common&v=2.0.0)
 * [swarm](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=swarm&v=2.0.0)
 * [kubernetes](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=kubernetes&v=2.0.0)
3. [Launch Brooklyn](https://brooklyn.apache.org/v/latest/start/running.html) and add the Clocker blueprints to the catalog by using the [`clocker.bom` file](../clocker.bom) that lists the catalog items:
 * If you'd like to use the command line to do this, you will need the [Apache Brooklyn CLI](https://brooklyn.apache.org/v/latest/ops/cli/index.html) and run `br add-catalog clocker.bom`. For more details, see the [docs](http://brooklyn.apache.org/v/latest/ops/cli/index.html).
 * Alternatively, if you'd like to use the web-console, choose the "Composer" tab, click the "Catalog" button, paste the contents of the `clocker.bom` file into the online editor, and click "Deploy". For more details, see the [docs](http://brooklyn.apache.org/v/latest/ops/catalog/).
4. [Setup a location](http://brooklyn.apache.org/v/latest/ops/locations/) for where you want to deploy to. This can be done by defining your location configuration in a .bom file and deploying using the `br` CLI. Alternatively, the web-console can be used: click on the Catalog tag, then the "+" button", choose "Location", and follow the instructions in the wizard. We came up with location's templates to add to your catalog, that you can use out of the box for [AWS](locations/aws-example-location.bom), [SoftLayer](locations/sl-example-location.bom), [Azure](locations/azure-example-location.bom), [GCE](locations/gce-example-location.bom) and [Blue Box](locations/bb-example-location.bom). For more information, head to the [tutorial section](swarm-cluster.html#setup-a-cloud-location).

   *Please note that we recommend the [official Centos 7 images](https://wiki.centos.org/Cloud/AWS). Images from other providers may be less functional or incompatible.*

5. The catalog items added previously will be available in the Brooklyn quick launch. In the web-console, from the Home tab click the "Add application" button, choose your application, then your location, and any custom configuration options such as the size of cluster. Then click Deploy. For more details, see the [docs](http://brooklyn.apache.org/v/latest/ops/gui/blueprints.html#launching-from-the-catalog).
  ![Quick-launch of a Swarm or Kubernetes cluster]({{site.baseurl}}/assets/images/quick-launch-brooklyn.png)
6. View your application in the "Applications" tab. Once the app is deployed, this will show important information such as the connection details. Click on the entity (i.e. component) in the tree view, and the "Sensors" tab to see details of that entity. For more details, the [docs](see http://brooklyn.apache.org/v/latest/ops/gui/managing.html).

{% endtab %}

{% endtabs %}
