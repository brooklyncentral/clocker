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

echo "01" > ca-cert.srl
echo "subjectAltName = IP:$1" > repo.cnf

mkdir certs

openssl genrsa -out certs/repo-key.pem 2048
openssl req -subj "/CN=$1" -new -key certs/repo-key.pem -out certs/repo.csr
openssl x509 -req -days 365 -in certs/repo.csr -CA ca-cert.pem -CAkey ca-key.pem -out certs/repo-cert.pem -extfile repo.cnf