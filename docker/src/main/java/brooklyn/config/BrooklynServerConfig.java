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
package brooklyn.config;

import static brooklyn.entity.basic.ConfigKeys.newStringConfigKey;
import io.brooklyn.camp.CampPlatform;

import java.io.File;
import java.net.URI;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogLoadMode;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.os.Os;

/** Config keys for the brooklyn server */
public class BrooklynServerConfig {

    private static final Logger log = LoggerFactory.getLogger(BrooklynServerConfig.class);

    /**
     * Provided for setting; consumers should use {@link #getMgmtBaseDir(ManagementContext)}
     */
    public static final ConfigKey<String> MGMT_BASE_DIR = newStringConfigKey(
            "brooklyn.base.dir", "Directory for reading and writing all brooklyn server data", 
            Os.fromHome(".brooklyn"));
    
    @Deprecated /** @deprecated since 0.7.0 use BrooklynServerConfig routines */
    // copied here so we don't have back-ref to BrooklynConfigKeys
    public static final ConfigKey<String> BROOKLYN_DATA_DIR = newStringConfigKey(
            "brooklyn.datadir", "Directory for writing all brooklyn data");

    public static final String DEFAULT_PERSISTENCE_CONTAINER_NAME = "brooklyn-persisted-state";
    /** on file system, the 'data' subdir is used so that there is an obvious place to put backup dirs */ 
    public static final String DEFAULT_PERSISTENCE_DIR_FOR_FILESYSTEM = Os.mergePaths(DEFAULT_PERSISTENCE_CONTAINER_NAME, "data");
    
    /**
     * Provided for setting; consumers should query the management context persistence subsystem
     * for the actual target, or use {@link #resolvePersistencePath(String, StringConfigMap, String)}
     * if trying to resolve the value
     */
    public static final ConfigKey<String> PERSISTENCE_DIR = newStringConfigKey(
        "brooklyn.persistence.dir", 
        "Directory or container name for writing brooklyn persisted state");

    public static final ConfigKey<String> PERSISTENCE_LOCATION_SPEC = newStringConfigKey(
        "brooklyn.persistence.location.spec", 
        "Optional location spec string for an object store (e.g. jclouds:swift:URL) where persisted state should be kept;"
        + "if blank or not supplied, the file system is used"); 

    public static final ConfigKey<Boolean> PERSISTENCE_BACKUPS_REQUIRED =
        ConfigKeys.newBooleanConfigKey("brooklyn.persistence.backups.required",
            "Whether a backup should always be made of the persistence directory; "
            + "if true, it will fail if this operation is not permitted (e.g. jclouds-based cloud object stores); "
            + "if false, the persistence store will be overwritten with changes (but files not removed if they are unreadable); "
            + "if null or not set, the legacy beahviour of creating backups where possible (e.g. file system) is currently used, "
            + "but this may be changed in future versions");

    public static final ConfigKey<String> BROOKLYN_CATALOG_URL = ConfigKeys.newStringConfigKey("brooklyn.catalog.url",
        "The URL of a catalog.xml descriptor; absent for default (~/.brooklyn/catalog.xml), " +
        "or empty for no URL (use default scanner)",
        new File(Os.fromHome(".brooklyn/catalog.xml")).toURI().toString());

    public static final ConfigKey<CatalogLoadMode> CATALOG_LOAD_MODE = ConfigKeys.newConfigKey(CatalogLoadMode.class,
            "brooklyn.catalog.mode",
            "The mode the management context should use to load the catalog when first starting",
            CatalogLoadMode.LOAD_BROOKLYN_CATALOG_URL);

    public static final ConfigKey<Boolean> USE_OSGI = ConfigKeys.newBooleanConfigKey("brooklyn.osgi.enabled",
        "Whether OSGi is enabled, defaulting to true", true);

    public static final ConfigKey<CampPlatform> CAMP_PLATFORM = ConfigKeys.newConfigKey(CampPlatform.class, "brooklyn.camp.platform",
        "Config set at brooklyn management platform to find the CampPlatform instance (bi-directional)");

    public static final AttributeSensor<ManagementContext.PropertiesReloadListener> PROPERTIES_RELOAD_LISTENER = Sensors.newSensor(
            ManagementContext.PropertiesReloadListener.class, "brooklyn.management.propertiesReloadListenet", "Properties reload listener");

    public static String getMgmtBaseDir(ManagementContext mgmt) {
        return getMgmtBaseDir(mgmt.getConfig());
    }
    
    public static String getMgmtBaseDir(StringConfigMap brooklynProperties) {
        String base = (String) brooklynProperties.getConfigRaw(MGMT_BASE_DIR, true).orNull();
        if (base==null) {
            base = brooklynProperties.getConfig(BROOKLYN_DATA_DIR);
            if (base!=null)
                log.warn("Using deprecated "+BROOKLYN_DATA_DIR.getName()+": use "+MGMT_BASE_DIR.getName()+" instead; value: "+base);
        }
        if (base==null) base = brooklynProperties.getConfig(MGMT_BASE_DIR);
        return Os.tidyPath(base)+File.separator;
    }
    public static String getMgmtBaseDir(Map<String,?> brooklynProperties) {
        String base = (String) brooklynProperties.get(MGMT_BASE_DIR.getName());
        if (base==null) base = (String) brooklynProperties.get(BROOKLYN_DATA_DIR.getName());
        if (base==null) base = MGMT_BASE_DIR.getDefaultValue();
        return Os.tidyPath(base)+File.separator;
    }
    
    protected static String resolveAgainstBaseDir(StringConfigMap brooklynProperties, String path) {
        if (!Os.isAbsolutish(path)) path = Os.mergePaths(getMgmtBaseDir(brooklynProperties), path);
        return Os.tidyPath(path);
    }
    
    /** @deprecated since 0.7.0 use {@link #resolvePersistencePath(String, StringConfigMap, String)} */
    public static String getPersistenceDir(ManagementContext mgmt) {
        return getPersistenceDir(mgmt.getConfig());
    }
    /** @deprecated since 0.7.0 use {@link #resolvePersistencePath(String, StringConfigMap, String)} */ 
    public static String getPersistenceDir(StringConfigMap brooklynProperties) {
        return resolvePersistencePath(null, brooklynProperties, null);
    }
    
    /**
     * @param optionalSuppliedValue
     *     An optional value which has been supplied explicitly
     * @param brooklynProperties
     *     The properties map where the persistence path should be looked up if not supplied,
     *     along with finding the brooklyn.base.dir if needed (using file system persistence
     *     with a relative path)
     * @param optionalObjectStoreLocationSpec
     *     If a location spec is supplied, this will return a container name suitable for use
     *     with the given object store based on brooklyn.persistence.dir; if null this method
     *     will return a full file system path, relative to the brooklyn.base.dir if the
     *     configured brooklyn.persistence.dir is not absolute
     * @return The container name or full path for where persist state should be kept
     */
    public static String resolvePersistencePath(String optionalSuppliedValue, StringConfigMap brooklynProperties, String optionalObjectStoreLocationSpec) {
        String path = optionalSuppliedValue;
        if (path==null) path = brooklynProperties.getConfig(PERSISTENCE_DIR);
        if (optionalObjectStoreLocationSpec==null) {
            // file system
            if (path==null) path=DEFAULT_PERSISTENCE_DIR_FOR_FILESYSTEM;
            return resolveAgainstBaseDir(brooklynProperties, path);
        } else {
            // obj store
            if (path==null) path=DEFAULT_PERSISTENCE_CONTAINER_NAME;
            return path;
        }
    }

    public static File getBrooklynWebTmpDir(ManagementContext mgmt) {
        String brooklynMgmtBaseDir = getMgmtBaseDir(mgmt);
        File webappTempDir = new File(Os.mergePaths(brooklynMgmtBaseDir, "planes", mgmt.getManagementPlaneId(), mgmt.getManagementNodeId(), "jetty"));
        try {
            FileUtils.forceMkdir(webappTempDir);
            Os.deleteOnExitRecursivelyAndEmptyParentsUpTo(webappTempDir, new File(brooklynMgmtBaseDir)); 
            return webappTempDir;
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            IllegalStateException e2 = new IllegalStateException("Cannot create working directory "+webappTempDir+" for embedded jetty server: "+e, e);
            log.warn(e2.getMessage()+" (rethrowing)");
            throw e2;
        }
    }

    /**
     * @return the CAMP platform associated with a management context, if there is one.
     */
    public static Maybe<CampPlatform> getCampPlatform(ManagementContext mgmt) {
        CampPlatform result = mgmt.getConfig().getConfig(BrooklynServerConfig.CAMP_PLATFORM);
        if (result!=null) return Maybe.of(result);
        return Maybe.absent("No CAMP Platform is registered with this Brooklyn management context.");
    }

    /**
     * @return {@link ManagementContext#getManagementNodeUri()}, located in this utility class for convenience.
     */
    public static Maybe<URI> getBrooklynWebUri(ManagementContext mgmt) {
        return mgmt.getManagementNodeUri();
    }
    
}
