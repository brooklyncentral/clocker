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
package org.jclouds.docker.suppliers;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;

import java.io.*;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.*;

import brooklyn.util.ResourceUtils;
import brooklyn.util.crypto.SecureKeys;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jclouds.domain.Credentials;
import org.jclouds.http.HttpUtils;
import org.jclouds.http.config.SSLModule.TrustAllCerts;
import org.jclouds.location.Provider;
import org.jclouds.util.Closeables2;

import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.io.Files;

@Singleton
public class SSLContextWithKeysSupplier implements Supplier<SSLContext> {
    private final TrustManager[] trustManager;
    private final Supplier<Credentials> creds;

    @Inject
    SSLContextWithKeysSupplier(@Provider Supplier<Credentials> creds, HttpUtils utils, TrustAllCerts trustAllCerts) {
        this.trustManager = utils.trustAllCerts() ? new TrustManager[]{trustAllCerts} : null;
        this.creds = creds;
    }

    public SSLContextWithKeysSupplier(Supplier<Credentials> creds) {
        this.trustManager = null;
        this.creds = creds;
    }

    @Override
    public SSLContext get() {
        Credentials currentCreds = checkNotNull(creds.get(), "credential supplier returned null");
        try {
            try (InputStream caCert = Files.asByteSource(new File("/Users/csabapalfi/clocker/examples/target/brooklyn-clocker-dist/brooklyn-clocker/conf/ca-cert.pem")).openStream()) {
                X509Certificate caCertificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(caCert);
                KeyStore store = SecureKeys.newKeyStore();
                store.setCertificateEntry("ca", caCertificate);
                X509TrustManager trustManager = SecureKeys.getTrustManager(caCertificate);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                X509Certificate certificate = getCertificate(loadFile(currentCreds.identity));
                PrivateKey privateKey = getKey(loadFile(currentCreds.credential));
                sslContext.init(new KeyManager[]{new InMemoryKeyManager(certificate, privateKey)}, new TrustManager[]{trustManager}, new SecureRandom());
                return sslContext;
            }
        } catch (NoSuchAlgorithmException e) {
            throw propagate(e);
        } catch (KeyManagementException|KeyStoreException e) {
            throw propagate(e);
        } catch (CertificateException e) {
            throw propagate(e);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    private static X509Certificate getCertificate(String certificate) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(certificate.getBytes(Charsets.UTF_8)));
        } catch (CertificateException ex) {
            throw new RuntimeException("Invalid certificate", ex);
        }
    }

    private static PrivateKey getKey(String privateKey) {
        PEMParser pemParser = new PEMParser(new StringReader(privateKey));
        try {
            Object object = pemParser.readObject();
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            KeyPair keyPair = converter.getKeyPair((PEMKeyPair) object);
            return keyPair.getPrivate();
        } catch (IOException ex) {
            throw new RuntimeException("Invalid private key", ex);
        } finally {
            Closeables2.closeQuietly(pemParser);
        }
    }

    private static String loadFile(final String filePath) throws IOException {
        return Files.toString(new File(filePath), Charsets.UTF_8);
    }

    private static class InMemoryKeyManager extends X509ExtendedKeyManager {
        private static final String DEFAULT_ALIAS = "docker";

        private final X509Certificate certificate;

        private final PrivateKey privateKey;

        public InMemoryKeyManager(final X509Certificate certificate, final PrivateKey privateKey)
                throws IOException, CertificateException {
            this.certificate = certificate;
            this.privateKey = privateKey;
        }

        @Override
        public String chooseClientAlias(final String[] keyType, final Principal[] issuers,
                                        final Socket socket) {
            return DEFAULT_ALIAS;
        }

        @Override
        public String chooseServerAlias(final String keyType, final Principal[] issuers,
                                        final Socket socket) {
            return DEFAULT_ALIAS;
        }

        @Override
        public X509Certificate[] getCertificateChain(final String alias) {
            return new X509Certificate[]{certificate};
        }

        @Override
        public String[] getClientAliases(final String keyType, final Principal[] issuers) {
            return new String[]{DEFAULT_ALIAS};
        }

        @Override
        public PrivateKey getPrivateKey(final String alias) {
            return privateKey;
        }

        @Override
        public String[] getServerAliases(final String keyType, final Principal[] issuers) {
            return new String[]{DEFAULT_ALIAS};
        }
    }

}
