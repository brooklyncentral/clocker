FROM gliderlabs/alpine:3.1

RUN apk-install openjdk7-jre-base
RUN apk-install bash

ADD examples/target/brooklyn-clocker-dist.tar.gz .

WORKDIR brooklyn-clocker

VOLUME /root/.brooklyn

EXPOSE 8081

CMD bin/clocker.sh
