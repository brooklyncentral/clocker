## What's libswarm
A minimalist toolkit to compose network services, specifically designed for Docker.

An interesting intro is available at 
http://www.tech-d.net/2014/07/03/libswarm/

Using that guide, you can, for example, run something like:

    $ swarmd 'dockerserver tcp://localhost:2376' 'dockerclient tcp://localhost:2375'
    copying task output from &libswarm.receiverWrapper{Receiver:(*libchan.PipeReceiver)(0xc210000508)} to &libswarm.senderWrapper{Sender:(*libchan.PipeSender)(0xc210000650)}
    Starting Docker server...

This will start a Rest API server at `localhost:2376` that delegates the execution to a running docker engine at `localhost:2375`

In fact, a subsequent call like the following:

    sudo docker -H tcp://localhost:2376 ps

will produce the usual list of containers and the following log line:   

    Calling GET /containers/json

### Limitations    
Currently, `swarmd` allows you to `aggregate` multiple docker engines, but the containers created on the aggregated swarmd service will 
not be able to ping each other, in other words, they won't see each other. 

Docker community is working on a new links implementation that should help with it
