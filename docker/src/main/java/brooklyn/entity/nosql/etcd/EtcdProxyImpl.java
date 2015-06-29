/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.nosql.etcd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.event.feed.ConfigToAttributes;

public class EtcdProxyImpl extends EtcdNodeImpl implements EtcdProxy {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdProxy.class);

    public void init() {
       super.init();
       ConfigToAttributes.apply(this, EtcdProxy.ETCD_CLUSTER_NAME);
       ConfigToAttributes.apply(this, EtcdProxy.ETCD_CLUSTER_URL);
       LOG.info("Starting {} node: {}", "proxy", getNodeName());
    }

    @Override
    public EtcdProxyDriver getDriver() {
        return (EtcdProxyDriver) super.getDriver();
    }

    @Override
    public Class<EtcdProxyDriver> getDriverInterface() {
        return EtcdProxyDriver.class;
    }

    @Override
    public void joinCluster(String nodeName, String nodeAddress) {
    }

    @Override
    public void leaveCluster(String nodeName) {
    }

    @Override
    public boolean hasJoinedCluster() { return true; }

}
