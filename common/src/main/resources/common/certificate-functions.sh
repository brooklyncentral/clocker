#!/usr/bin/env bash

function generate_key () {
  openssl genrsa -out $1 2048
}

function generate_conf () {
  local CNF=$1
  shift 1
  local ix=0
  cat > ${CNF} <<-EOF
[ req ]
req_extensions=v3_req
distinguished_name=req_distinguished_name

[ req_distinguished_name ]
commonName                      = Common Name (eg, your name or your server\'s hostname)
commonName_max                  = 64

[ v3_req ]
subjectAltName = @alt_names

[ alt_names ]
EOF

while [ $# -gt 0 ] ; do
  ix=$(( $ix + 1 ))
  echo "IP.${ix} = $1" >> ${CNF}
  shift 1
done
}

function generate_csr () {
  openssl req -config $1 -new -key $2 -days 1825 -subj "/CN=$(hostname)" -out $3
}

function failwith() {
  local err=$?
  1>&2 echo "[CLOCKER] $1"
  exit $err
}

function getcert() {
  local url=$1
  local file=$2
  curl -L ${url} --output ${file} --write-out "%{http_code}"  | grep 200 ||
    failwith "${file} not received from CA"
}
