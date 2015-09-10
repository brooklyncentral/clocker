package brooklyn.networking.sdn.weave;


import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import java.util.Set;

/**
 * Created by graememiller on 09/09/2015.
 */
public class WeaveScopeImpl extends SoftwareProcessImpl implements WeaveScope {




    @Override
    public Class getDriverInterface() {
        return WeaveScopeDriver.class;
    }

    @Override
    protected Set<Integer> getRequiredOpenPorts() {
        return MutableSet.of(config().get(WeaveScope.WEAVE_SCOPE_PORT));
    }
}
