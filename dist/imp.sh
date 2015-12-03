#!/bin/bash
#
# Generate CA Certs
# adapted from the OBuildFactory project code at
# https://github.com/hgomez/obuildfactory/blob/master/openjdk7/macosx/build.sh
# (Apache License 2.0)
#
function cacerts_gen()
{
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
