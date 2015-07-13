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
package org.jclouds.http.okhttp.config;

import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.jclouds.http.HttpCommandExecutorService;
import org.jclouds.http.HttpUtils;
import org.jclouds.http.config.ConfiguresHttpCommandExecutorService;
import org.jclouds.http.config.SSLModule;
import org.jclouds.http.okhttp.OkHttpClientSupplier;
import org.jclouds.http.okhttp.OkHttpCommandExecutorService;

import com.google.common.base.Supplier;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.squareup.okhttp.OkHttpClient;

/**
 * Configures the {@link OkHttpCommandExecutorService}.
 *
 * Note that this uses threads.
 */
@ConfiguresHttpCommandExecutorService
public class OkHttpCommandExecutorServiceModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new SSLModule());
        bind(HttpCommandExecutorService.class).to(OkHttpCommandExecutorService.class).in(Scopes.SINGLETON);
        bind(OkHttpClient.class).toProvider(OkHttpClientProvider.class).in(Scopes.SINGLETON);
    }

    private static final class OkHttpClientProvider implements Provider<OkHttpClient> {
        private final HostnameVerifier verifier;
        private final Supplier<SSLContext> untrustedSSLContextProvider;
        private final HttpUtils utils;
        private final OkHttpClientSupplier clientSupplier;

        @Inject
        OkHttpClientProvider(HttpUtils utils, @Named("untrusted") HostnameVerifier verifier,
                             @Named("untrusted") Supplier<SSLContext> untrustedSSLContextProvider, OkHttpClientSupplier clientSupplier) {
            this.utils = utils;
            this.verifier = verifier;
            this.untrustedSSLContextProvider = untrustedSSLContextProvider;
            this.clientSupplier = clientSupplier;
        }

        @Override
        public OkHttpClient get() {
            OkHttpClient client = clientSupplier.get();
            client.setConnectTimeout(utils.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            client.setReadTimeout(utils.getSocketOpenTimeout(), TimeUnit.MILLISECONDS);
            // do not follow redirects since https redirects don't work properly
            // ex. Caused by: java.io.IOException: HTTPS hostname wrong: should be
            // <adriancole.s3int0.s3-external-3.amazonaws.com>
            client.setFollowRedirects(false);

            if (utils.relaxHostname()) {
                client.setHostnameVerifier(verifier);
            }
//            if (utils.trustAllCerts()) {
//                client.setSslSocketFactory(untrustedSSLContextProvider.get().getSocketFactory());
//            }

            return client;
        }
    }

}
