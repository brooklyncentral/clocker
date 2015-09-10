package brooklyn.networking.sdn.weave;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * Created by graememiller on 09/09/2015.
 */
@Catalog(name = "Weave Scope", description = "Weave Scope", iconUrl = "classpath://weaveworks-logo.png")
@ImplementedBy(WeaveScopeImpl.class)
public interface WeaveScope extends SoftwareProcess{

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.6.0");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "https://github.com/weaveworks/scope/releases/download/v${version}/scope");

    @SetFromFlag("weavePort")
    ConfigKey<Integer> WEAVE_SCOPE_PORT = ConfigKeys.newIntegerConfigKey("weave.scope.port", "Weave scope port", 4040);
}
