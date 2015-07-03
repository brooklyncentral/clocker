#!/bin/bash -e
#
# Copyright 2014-2015 by Cloudsoft Corporation Limited
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

#set -x # debug

echo "extendedKeyUsage = clientAuth" > client.cnf
echo "subjectAltName = IP:$1" > server.cnf
echo "01" > ca-cert.srl

openssl genrsa -out server-key.pem 2048
openssl req -subj '/CN=*' -new -key server-key.pem -out server.csr
openssl x509 -req -days 365 -in server.csr -CA ca-cert.pem -CAkey ca-key.pem -out server-cert.pem -extfile server.cnf
openssl rsa -in server-key.pem -out server-key.pem
openssl genrsa -out client-key.pem 2048
openssl req -subj '/CN=*' -new -key client-key.pem -out client.csr
openssl x509 -req -days 365 -in client.csr -CA ca-cert.pem -CAkey ca-key.pem -out client-cert.pem -extfile client.cnf
openssl rsa -in client-key.pem -out client-key.pem
