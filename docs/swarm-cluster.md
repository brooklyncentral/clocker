---
layout: base
title: Docker Swarm Documentation
---

### Overview
With Clocker, you can easily deploy and manage Docker Swarm clusters. They are production-ready infrastructure with TLS and high-availability.

You can also connect to existing infrastructure provisioned and managed externally, by specifying appropriate API endpoints.

### Architecture
![Docker Swarm architecture]({{site.baseurl}}/assets/images/swarm-architecture.png)

The Docker Swarm entity that comes with Clocker will deploy and manage the following components:

#### A load-balanced cluster of swarm managers
Swarm managers control a swarm's nodes and dictate the node on which containers are deployed.
We interact directly with the swarm manager cluster's load balancer as if it were a single docker node.
The load-balancer will redirect traffic to a healthy manager when a manager fails.  The replacer policy will detect the failure and replace the failed manager.

#### A cluster of swarm nodes
These nodes are where docker containers are deployed. The cluster has an AutoScalerPolicy and will scale up due to high CPU usage.

#### etcd Cluster
Used as a discovery backend for the swarm cluster.

#### CA Server
This is used to provide TLS certificates for the swarm cluster. This component is designed to be easily replaced. It is strongly recommended that this component is replaced with a production grade CA server of your choice.

### Location
Clocker currently supports deployments to **CentOS only**. We recommend to setup a location with the following setting:

- use at least 2GB RAM
- use a CentOS 7 based image

If you are using Brooklyn, it might a good idea to increase the entropy of your server. This would prevent installation speed being slowed. For more information, see [Entropy Troubleshooting](https://brooklyn.apache.org/v/latest/ops/troubleshooting/increase-entropy.html)

Finally, be aware that you might get in trouble because of your cloud provider's security group. For more information, please see the [troubleshooting page](troubleshooting.html#failed-to-find-machine-unique-group-on-node).

### Configuration 
The Docker Swarm entity comes with built-in configuration that allows you to control how your Docker Swarm will be deployed and manage.

#### Auto scaling
There is auto-scaling functionality included with a deployed Swarm cluster by default. There is currently no option to scale down. There are some configuration options that control how the scale is performed:

| Config Name             | Description                                                                                           |
|-------------------------|-------------------------------------------------------------------------------------------------------|
| swarm.initial.size      | The initial number of swarm nodes to create                                                           |
| swarm.max.size          | The maximum number of swarm nodes to scale up to                                                      |
| swarm.scaling.cpu.limit | The percentage limit at which we should scale up. This should be a double value between 0.00 and 1.00 |
| swarm.manager.size      | The number of swarm managers                                                                          |


#### Networking
The Swarm cluster is automatically setup with an overlay network. This network can be used by containers deployed to the swarm cluster to communicate. There are some configuration options that control networking:

| Config Name          | Description                                                                                  |
|----------------------|----------------------------------------------------------------------------------------------|
| swarm.defaultnetwork | The ID of the default network to set. When deploying to this swarm, this network can be used |
| swarm.discovery.url  | URL of a provided discovery mechanism for the swarm                                          |
| swarm.port           | The TCP port the Swarm manager listens on                                                    |

#### Hardware
The Swarm nodes can be provisioned on with specific hardware requirements. There are some configuration options that control what machines are provisioned:

| Config Name    | Description                                    |
|----------------|------------------------------------------------|
| swarm.minCores | Minimum CPU cores for provisioning Swarm nodes |
| swarm.minRam   | Minimum RAM for provisioning Swarm nodes       |

#### Troubleshooting
For any problems please consult the [troubleshooting section here](troubleshooting.html).
