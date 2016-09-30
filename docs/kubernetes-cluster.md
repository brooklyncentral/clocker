---
layout: base
title: Kubernetes Cluster Documentation
---

### Overview
With Clocker, you can easily deploy and manage Kubernetes clusters. They are production-ready infrastructure with TLS, high-availability and extensions like Flannel, Calico and Canal for networking.

Can also connect to existing infrastructure provisioned and managed externally, by specifying appropriate API endpoints.

### Architecture
![Kubernetes cluster architecture]({{site.baseurl}}/assets/images/kubernetes-architecture.png)

The Kubernetes entity that comes with Clocker will deploy and manage the following components:

#### Kubernetes cluster
This Kubernetes cluster contains a manager and a configurable number of workers.
It requires a pre-existing discovery mechanism and references to a CA server entity.
The cluster has an AutoScalerPolicy and will scale up due to high CPU usage. It also has a replacer policy that will detect the failure and replace the failed worker.

#### etcd Cluster
Used as a discovery backend for the Kubernetes cluster.

#### CA Server
This is used to provide TLS certificates for the Kubernetes cluster. This component is designed to be easily replaced. It is strongly recommended that this component is replaced with a production grade CA server of your choice.

### Configuration 
The Kubernetes entity comes with built-in configuration that allows you to control how your Kubernetes cluster will be deployed and manage.

#### Auto scaling
There is auto-scaling functionality included with a deployed Kubernetes cluster by default. There is currently no option to scale down. There are some configuration options that control how the scale is performed:

| Config Name                  | Description                                                                                           |
|------------------------------|-------------------------------------------------------------------------------------------------------|
| kubernetes.initial.size      | The initial size of the Kubernetes cluster                                                            |
| kubernetes.max.size          | Maximum size the Kubernetes cluster can be scaled to                                                  |
| kubernetes.scaling.cpu.limit | The percentage limit at which we should scale up. This should be a double value between 0.00 and 1.00 |
| etcd.initial.size            | The initial size of Etcd cluster                                                                      |

#### Networking
The Kubernetes cluster is automatically setup with an overlay network. This network can be used by containers deployed to the Kubernetes cluster to communicate. There are some configuration options that control networking:

| Config Name               | Description                                    |
|---------------------------|------------------------------------------------|
| kubernetes.pod.cidr       | Pod IP Range to use for the Kubernetes cluster |
| kubernetes.service.cidr   | Kubernetes Service IP Range                    |
| kubernetes.apiserver.port | Kubernetes API Server Port                     |
| kubernetes.address        | Kubernetes IP                                  |
| kubernetes.dns.address    | Kubernetes DNS IP                              |
| kubernetes.dns.domain     | Kubernetes DNS Domain                          |

#### Kubernetes specific
There are some configuration options that control Kubernetes specific parts:

| Config Name             | Description                    |
|-------------------------|--------------------------------|
| kubernetes.version      | Version of Kubernetes to use   |
| kubernetes.cluster.name | Name of the Kubernetes cluster |
| kubernetes.user         | Kubernetes username            |
| kubernetes.password     | Kubernetes password            |
