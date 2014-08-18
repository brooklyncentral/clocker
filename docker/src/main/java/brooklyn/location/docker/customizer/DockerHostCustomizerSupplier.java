package brooklyn.location.docker.customizer;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

import brooklyn.entity.Entity;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.jclouds.JcloudsLocationCustomizer;
import brooklyn.location.jclouds.networking.JcloudsLocationSecurityGroupCustomizer;
import brooklyn.util.config.ConfigBag;

/**
  * A supplier that can be referenced from Brooklyn properties with:
  * <p/>
  * {@code brooklyn.location.named.<name>.customizersSupplierType=brooklyn.location.docker.supplier.DockerHostCustomizerSupplier}
  */
public class DockerHostCustomizerSupplier implements Supplier<Collection<JcloudsLocationCustomizer>> {

    private static final Logger LOG = LoggerFactory.getLogger(DockerHostCustomizerSupplier.class);
    private static final String NULL_APPLICATION_CONTEXT = "_NULL_";

    final String applicationContext;
    final ConfigBag configBag;

    public DockerHostCustomizerSupplier(ConfigBag config) {
        this.configBag = config;
        Object context = config.get(LocationConfigKeys.CALLER_CONTEXT);
        if (context instanceof Entity) {
            applicationContext = ((Entity) context).getApplicationId();
            LOG.debug("Created DockerHostCustomizerSupplier supplier with application context " + applicationContext);
        } else {
            applicationContext = NULL_APPLICATION_CONTEXT;
            LOG.info("Created DockerHostCustomizerSupplier supplier with null application context ");
        }
    }

    @Override
    public Collection<JcloudsLocationCustomizer> get() {
        return ImmutableList.<JcloudsLocationCustomizer>of(
                JcloudsLocationSecurityGroupCustomizer.getInstance(applicationContext));
    }
}
