/*
 * Copyright 2014-2016 by Cloudsoft Corporation Limited
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
package org.apache.brooklyn.entity.proxy.haproxy;

import org.apache.brooklyn.entity.proxy.AbstractControllerImpl;

public class HAProxyControllerImpl extends AbstractControllerImpl implements HAProxyController {

    @Override
    protected void reconfigureService() {
        getDriver().reconfigureService();
    }

    @Override
    public void reload() {
        reconfigureService();
    }

    @Override
    public Class getDriverInterface() {
        return HAProxyDriver.class;
    }

    @Override
    public HAProxyDriver getDriver() {
        return HAProxyDriver.class.cast(super.getDriver());
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    protected void disconnectSensors() {
        disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }

    @Override
    protected String inferUrl() {
        return inferUrl(true); // Require Brooklyn accessible hostname
    }

}
