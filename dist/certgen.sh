#!/bin/bash
#
# Copyright 2015 by Cloudsoft Corporation Limited
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Generate CA Certs
#
# adapted from the OBuildFactory project code at
# https://github.com/hgomez/obuildfactory/blob/master/openjdk7/macosx/build.sh

function cacerts_gen() {
    local DESTCERTS=$1
    local TMPCERTSDIR=`mktemp -d certs-XXXXXX`

    pushd $TMPCERTSDIR > /dev/null
    wget http://curl.haxx.se/ca/cacert.pem
    cat cacert.pem | awk '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/{ print $0; }' > cacert-clean.pem
    rm -f cacerts cert_*
    awk '/-----BEGIN CERTIFICATE-----/{x=++i;}{print > "cert_"x;}' cacert-clean.pem

    for CERT_FILE in cert_*; do
        ALIAS=$(basename ${CERT_FILE})
        keytool -noprompt -trustcacerts -import -alias ${ALIAS} -keystore cacerts -storepass 'changeit' -file ${CERT_FILE} || :
        rm -f $CERT_FILE
    done

    rm -f cacert.pem cacert-clean.pem
    cp cacerts $DESTCERTS

    popd > /dev/null
    rm -rf $TMPCERTSDIR
}

cacerts_gen $1
