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
package brooklyn.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.event.basic.BasicConfigKey.BasicConfigKeyOverwriting;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.text.Strings;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;

/**
 * This is the actual type of a brooklyn object instance at runtime,
 * which can change from the static {@link BrooklynType}, and can change over time;
 * for this reason it does *not* implement BrooklynType, but 
 * callers can call {@link #getSnapshot()} to get a snapshot such instance.  
 */
public abstract class BrooklynDynamicType<T extends BrooklynObject, AbstractT extends AbstractBrooklynObject> {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynDynamicType.class);

    protected final Class<? extends T> brooklynClass;
    protected final AbstractT instance;
    protected volatile String name;
    
    /** 
     * Map of config keys (and their fields) on this instance, by name.
     */
    protected final Map<String,FieldAndValue<ConfigKey<?>>> configKeys = new ConcurrentHashMap<String, FieldAndValue<ConfigKey<?>>>();

    private volatile BrooklynTypeSnapshot snapshot;
    private final AtomicBoolean snapshotValid = new AtomicBoolean(false);

    @SuppressWarnings("unchecked")
    public BrooklynDynamicType(AbstractT instance) {
        this((Class<? extends T>) instance.getClass(), instance);
    }
    public BrooklynDynamicType(Class<? extends T> clazz) {
        this(clazz, null);
    }
    protected BrooklynDynamicType(Class<? extends T> clazz, AbstractT instance) {
        this.brooklynClass = checkNotNull(clazz, "brooklyn class");
        this.instance = instance;
        // NB: official name is usually injected later, e.g. from AbstractEntity.setManagementContext
        this.name = (clazz.getCanonicalName() == null) ? clazz.getName() : clazz.getCanonicalName();
        
        buildConfigKeys(clazz, null, configKeys);
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} config keys: {}", (instance==null ? clazz.getName() : instance.getId()), Joiner.on(", ").join(configKeys.keySet()));
    }
    
    protected abstract BrooklynTypeSnapshot newSnapshot();

    protected void invalidateSnapshot() {
        snapshotValid.set(false);
    }

    public void setName(String name) {
        if (Strings.isBlank(name)) {
            throw new IllegalArgumentException("Invalid name "+(name == null ? "null" : "'"+name+"'")+"; name must be non-empty and not just white space");
        }
        this.name = name;
        invalidateSnapshot();
    }
    
    public synchronized BrooklynType getSnapshot() {
        return refreshSnapshot();
    }
    
    public Class<? extends T> getBrooklynClass() {
        return brooklynClass;
    }
    
    // --------------------------------------------------
    
    /**
     * ConfigKeys available on this entity.
     */
    public Map<String,ConfigKey<?>> getConfigKeys() {
        return Collections.unmodifiableMap(value(configKeys));
    }

    /**
     * ConfigKeys available on this entity.
     */
    public ConfigKey<?> getConfigKey(String keyName) { 
        return value(configKeys.get(keyName)); 
    }

    /** field where a config key is defined, for use getting annotations. note annotations are not inherited. */
    public Field getConfigKeyField(String keyName) { 
        return field(configKeys.get(keyName)); 
    }

    protected BrooklynTypeSnapshot refreshSnapshot() {
        if (snapshotValid.compareAndSet(false, true)) {
            snapshot = newSnapshot();
        }
        return snapshot;
    }

    /**
     * Finds the config keys defined on the entity's class, statics and optionally any non-static (discouraged).
     * Prefers keys which overwrite other keys, and prefers keys which are lower in the hierarchy;
     * logs warnings if there are two conflicting keys which don't have an overwriting relationship.
     */
    protected static void buildConfigKeys(Class<? extends BrooklynObject> clazz, AbstractBrooklynObject optionalInstance, 
            Map<String, FieldAndValue<ConfigKey<?>>> configKeys) {
        ListMultimap<String,FieldAndValue<ConfigKey<?>>> configKeysAll = 
                ArrayListMultimap.<String, FieldAndValue<ConfigKey<?>>>create();
        
        for (Field f : FlagUtils.getAllFields(clazz)) {
            boolean isConfigKey = ConfigKey.class.isAssignableFrom(f.getType());
            if (!isConfigKey) {
                if (!HasConfigKey.class.isAssignableFrom(f.getType())) {
                    // neither ConfigKey nor HasConfigKey
                    continue;
                }
            }
            if (!Modifier.isStatic(f.getModifiers())) {
                // require it to be static or we have an instance
                LOG.warn("Discouraged use of non-static config key "+f+" defined in " + (optionalInstance!=null ? optionalInstance : clazz));
                if (optionalInstance==null) continue;
            }
            try {
                Object v = f.get(optionalInstance);

                if (v == null) {
                    LOG.warn("no value defined for config key field (skipping): "+f);
                } else {
                    ConfigKey<?> k = isConfigKey ? (ConfigKey<?>) v : ((HasConfigKey<?>) v).getConfigKey();
                    configKeysAll.put(k.getName(), new FieldAndValue<ConfigKey<?>>(f, k));
                }
            } catch (IllegalAccessException e) {
                LOG.warn("cannot access config key (skipping): "+f);
            }
        }
        LinkedHashSet<String> keys = new LinkedHashSet<String>(configKeysAll.keys());
        for (String kn: keys) {
            List<FieldAndValue<ConfigKey<?>>> kk = Lists.newArrayList(configKeysAll.get(kn));
            if (kk.size()>1) {
                // remove anything which extends another value in the list
                for (FieldAndValue<ConfigKey<?>> k: kk) {
                    ConfigKey<?> key = value(k);
                    if (key instanceof BasicConfigKeyOverwriting) {                            
                        ConfigKey<?> parent = ((BasicConfigKeyOverwriting<?>)key).getParentKey();
                        // find and remove the parent from consideration
                        for (FieldAndValue<ConfigKey<?>> k2: kk) {
                            if (value(k2) == parent)
                                configKeysAll.remove(kn, k2);
                        }
                    }
                }
                kk = Lists.newArrayList(configKeysAll.get(kn));
            }
            // multiple keys, not overwriting; if their values are the same then we don't mind
            FieldAndValue<ConfigKey<?>> best = null;
            for (FieldAndValue<ConfigKey<?>> k: kk) {
                if (best==null) {
                    best=k;
                } else {
                    Field lower = Reflections.inferSubbestField(k.field, best.field);
                    ConfigKey<? extends Object> lowerV = lower==null ? null : lower.equals(k.field) ? k.value : best.value;
                    if (best.value == k.value) {
                        // same value doesn't matter which we take (but take lower if there is one)
                        if (LOG.isTraceEnabled()) 
                            LOG.trace("multiple definitions for config key {} on {}; same value {}; " +
                                    "from {} and {}, preferring {}", 
                                    new Object[] {
                                    best.value.getName(), optionalInstance!=null ? optionalInstance : clazz,
                                    best.value.getDefaultValue(),
                                    k.field, best.field, lower});
                        best = new FieldAndValue<ConfigKey<?>>(lower!=null ? lower : best.field, best.value);
                    } else if (lower!=null) {
                        // different value, but one clearly lower (in type hierarchy)
                        if (LOG.isTraceEnabled()) 
                            LOG.trace("multiple definitions for config key {} on {}; " +
                                    "from {} and {}, preferring lower {}, value {}", 
                                    new Object[] {
                                    best.value.getName(), optionalInstance!=null ? optionalInstance : clazz,
                                    k.field, best.field, lower,
                                    lowerV.getDefaultValue() });
                        best = new FieldAndValue<ConfigKey<?>>(lower, lowerV);
                    } else {
                        // different value, neither one lower than another in hierarchy
                        LOG.warn("multiple ambiguous definitions for config key {} on {}; " +
                                "from {} and {}, values {} and {}; " +
                                "keeping latter (arbitrarily)", 
                                new Object[] {
                                best.value.getName(), optionalInstance!=null ? optionalInstance : clazz,
                                k.field, best.field, 
                                k.value.getDefaultValue(), best.value.getDefaultValue() });
                        // (no change)
                    }
                }
            }
            if (best==null) {
                // shouldn't happen
                LOG.error("Error - no matching config key from "+kk+" in class "+clazz+", even though had config key name "+kn);
                continue;
            } else {
                configKeys.put(best.value.getName(), best);
            }
        }
    }
    
    protected static class FieldAndValue<V> {
        public final Field field;
        public final V value;
        public FieldAndValue(Field field, V value) {
            this.field = field;
            this.value = value;
        }
        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("field", field).add("value", value).toString();
        }
    }
    
    protected static <V> V value(FieldAndValue<V> fv) {
        if (fv==null) return null;
        return fv.value;
    }
    
    protected static Field field(FieldAndValue<?> fv) {
        if (fv==null) return null;
        return fv.field;
    }

    protected static <V> Collection<V> value(Collection<FieldAndValue<V>> fvs) {
        List<V> result = new ArrayList<V>();
        for (FieldAndValue<V> fv: fvs) result.add(value(fv));
        return result;
    }

    protected static <K,V> Map<K,V> value(Map<K,FieldAndValue<V>> fvs) {
        Map<K,V> result = new LinkedHashMap<K,V>();
        for (K key: fvs.keySet())
            result.put(key, value(fvs.get(key)));
        return result;
    }

}
