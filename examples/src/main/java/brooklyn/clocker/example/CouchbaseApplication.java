/*
 * Copyright 2014 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.clocker.example;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.nosql.couchbase.CouchbaseCluster;
import brooklyn.entity.proxying.EntitySpec;

/**
 * Couchbase Cluster with 3 nodes
 */
@Catalog(name="Couchbase Cluster",
        description="Couchbase Cluster with 3 nodes",
        iconUrl="classpath://couchbase-logo.png")
public class CouchbaseApplication extends AbstractApplication implements StartableApplication {

    @Override
    public void initApp() {
        addChild(EntitySpec.create(CouchbaseCluster.class)
                .displayName("Couchbase Cluster").configure(CouchbaseCluster.INITIAL_SIZE, 3));
    }

}
