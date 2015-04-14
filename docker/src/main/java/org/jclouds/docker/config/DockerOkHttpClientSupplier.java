/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.docker.config;

import org.jclouds.docker.suppliers.SSLContextWithKeysSupplier;
import org.jclouds.http.okhttp.OkHttpClientSupplier;

import com.google.common.collect.ImmutableList;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.TlsVersion;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DockerOkHttpClientSupplier implements OkHttpClientSupplier {

   private final SSLContextWithKeysSupplier sslContextWithKeysSupplier;

   @Inject
   DockerOkHttpClientSupplier(SSLContextWithKeysSupplier sslContextWithKeysSupplier) {
      this.sslContextWithKeysSupplier = sslContextWithKeysSupplier;
   }

   @Override
   public OkHttpClient get() {
      OkHttpClient client = new OkHttpClient();
      ConnectionSpec tlsSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
              .tlsVersions(TlsVersion.TLS_1_0, TlsVersion.TLS_1_1, TlsVersion.TLS_1_2)
              .build();
      ConnectionSpec cleartextSpec = new ConnectionSpec.Builder(ConnectionSpec.CLEARTEXT)
              .build();
      client.setConnectionSpecs(ImmutableList.of(tlsSpec, cleartextSpec));
      client.setSslSocketFactory(sslContextWithKeysSupplier.get().getSocketFactory());
      return client;
   }

}
