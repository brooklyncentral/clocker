package clocker.compose.plan;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.typereg.AbstractFormatSpecificTypeImplementationPlan;
import org.apache.brooklyn.core.typereg.AbstractTypePlanTransformer;
import org.apache.brooklyn.core.typereg.BasicTypeImplementationPlan;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;

public class ComposeTypePlanTransformer extends AbstractTypePlanTransformer {

    private static final Logger log = LoggerFactory.getLogger(ComposeTypePlanTransformer.class);

    public static final String FEATURE_COMPOSE_ENABLED = BrooklynFeatureEnablement.FEATURE_PROPERTY_PREFIX + ".compose";

    private static final AtomicBoolean hasLoggedDisabled = new AtomicBoolean(false);

    private ManagementContext mgmt;

    static {
        BrooklynFeatureEnablement.setDefault(FEATURE_COMPOSE_ENABLED, true);
    }

    public static final String FORMAT = "brooklyn-tosca";

    public ComposeTypePlanTransformer() {
        super(FORMAT, "Compose for Clocker", "Clocker support for Docker Compose formatted blueprints.");
    }

    @Override
    public void setManagementContext(ManagementContext managementContext) {
        if (!isEnabled()) {
            if (!hasLoggedDisabled.compareAndSet(false, true)) {
                log.info("Not loading brooklyn-tosca platform: feature disabled");
            }
            return;
        }
        if (this.mgmt != null && this.mgmt != managementContext) {
            throw new IllegalStateException("Cannot switch mgmt context");
        } else if (this.mgmt == null) {
            this.mgmt = managementContext;
        }
    }

    protected EntitySpec<? extends Application> createApplicationSpec(Object input) {
        EntitySpec<BasicApplication> rootSpec = EntitySpec.create(BasicApplication.class);

        return rootSpec;
    }

    @Override
    public AbstractBrooklynObjectSpec<?, ?> createSpec(RegisteredType type, RegisteredTypeLoadingContext context) throws Exception {
        String planYaml = String.valueOf(type.getPlan().getPlanData());
        try {
            return createApplicationSpec(null);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    private boolean isEnabled() {
        return BrooklynFeatureEnablement.isEnabled(FEATURE_COMPOSE_ENABLED);
    }

    @Override
    public double scoreForTypeDefinition(String formatCode, Object catalogData) {
        return 0; // TODO: Not yet implemented
    }

    @Override
    public List<RegisteredType> createFromTypeDefinition(String formatCode, Object catalogData) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected double scoreForNullFormat(Object planData, RegisteredType type, RegisteredTypeLoadingContext context) {
        Maybe<Map<?, ?>> yamlMap = RegisteredTypes.getAsYamlMap(planData);
        if (yamlMap.isAbsent()) {
            return 0;
        }
        return 1;
    }

    @Override
    protected double scoreForNonmatchingNonnullFormat(String planFormat, Object planData, RegisteredType type, RegisteredTypeLoadingContext context) {
        return planFormat.equals(FORMAT) ? 0.9 : 0;
    }

    @Override
    protected Object createBean(RegisteredType type, RegisteredTypeLoadingContext context) throws Exception {
        return null;
    }

    public static class ComposeTypeImplementationPlan extends AbstractFormatSpecificTypeImplementationPlan<String> {
        public ComposeTypeImplementationPlan(RegisteredType.TypeImplementationPlan otherPlan) {
            super(FORMAT, String.class, otherPlan);
        }
        public ComposeTypeImplementationPlan(String planData) {
            this(new BasicTypeImplementationPlan(FORMAT, planData));
        }
    }
}
