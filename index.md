---
layout: base
title: Home
children:
- docs/index.md
- tutorials/index.md
---

## What is it?
Clocker for [Apache Brooklyn](https://brooklyn.apache.org/) is a set of open source tools designed to make working with [Docker](https://www.docker.com/) containers as simple as a few clicks. Clocker contains [Brooklyn blueprints](http://brooklyn.apache.org/v/latest/start/blueprints.html) to enable deployment and management of [Docker Swarms](https://www.docker.com/products/docker-swarm) and [Kubernetes clusters](http://kubernetes.io/).

## What can you do with it?

* You can easily deploy production grade Docker Swarms or Kubernetes clusters to a [range of clouds](http://brooklyn.apache.org/v/latest/ops/locations/index.html) (including [AWS](https://aws.amazon.com/), [Azure](https://azure.microsoft.com), [Google Cloud](https://cloud.google.com/), [IBM Softlayer](http://www.softlayer.com/), and [IBM BlueBox](https://www.blueboxcloud.com/)).
* You can manage and scale Swarms or clusters in real time. Automated in-life management will help keep your cluster healthy, by replacing failed nodes and auto-scaling the cluster when overloaded. This can be done as closed-loop or open-loop automation: either responding automatically, or driven by your operations team.

## How to use it?
If you are new to Clocker, you can head for our [Getting Started guide](tutorials/getting-started.html). Otherwise, check out our more [in-depth tutorials](tutorials) or simply use our [yaml example](examples/swarm.yaml){:target="blank"} to deploy a swarm cluster to an existing location.

<figure>
  <img src="{{site.baseurl}}/assets/images/quick-launch-amp-swarm.png" alt="Quick launch for Docker Swarm in AMP">
  <figcaption>Quick launch for Docker Swarm in AMP</figcaption>
</figure>
<figure>
  <img src="{{site.baseurl}}/assets/images/quick-launch-amp-kubernetes.png" alt="Quick launch for Kubernetes in AMP">
  <figcaption>Quick launch for Docker Swarm in AMP</figcaption>
</figure>
<figure>
  <img src="{{site.baseurl}}/assets/images/quick-launch-brooklyn.png" alt="Quick launch for Docker Swarm or Kubernetes in Brooklyn">
  <figcaption>Quick launch for Docker Swarm in AMP</figcaption>
</figure>

## Troubleshooting
For any problems please consult the [troubleshooting section](./docs/troubleshooting.html).

## License and Support
Clocker is released under the [Apache 2 License](https://www.apache.org/licenses/LICENSE-2.0).

If you would like a commercial support contract, or professional services assistance when trialling or using Clocker, please [contact Cloudsoft](http://www.cloudsoft.io/contact).