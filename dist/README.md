Clocker
=======

Clocker creates and manages a **[Docker](http://docker.io/)** cloud infrastructure. Clocker supports single-click deployment and runtime management of multi-node applications that can run on containers distributed across multiple hosts. Plugins are included for both **[Project Calico](https://github.com/Metaswitch/calico-docker/)** and **[Weave](https://github.com/weaveworks/weave/)** to provide seamless Software-Defined Networking integration. Application blueprints written for **[Apache Brooklyn](https://brooklyn.incubator.apache.org/)** can thus be deployed to a distributed Docker Cloud infrastructure.

[![Build Status](https://api.travis-ci.org/brooklyncentral/clocker.svg?branch=master)](https://travis-ci.org/brooklyncentral/clocker)

You must provide volume sources for a `.brooklyn` directory that is writeable, and your `.ssh` directory, and if you want to use a different port to 8080 or 8443 they must be forwarded on the host. A suitable startup command would be:

    % docker run -d -v ~/.brooklyn:/root/.brooklyn -v ~/.ssh:/root/.ssh -P \
        clockercentral/clocker:1.1.0-PREVIEW.20150901 \
        jclouds:aws-ec2:eu-west-1 calico

### Documentation

Please visit [clocker.io](https://brooklyncentral.github.io/clocker/) or the [wiki pages](https://github.com/brooklyncentral/clocker/wiki) for more details.

----
Copyright 2014-2015 by Cloudsoft Corporation Limited.
