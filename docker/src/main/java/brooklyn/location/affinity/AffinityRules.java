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
package brooklyn.location.affinity;

import java.util.List;
import java.util.Queue;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.util.javalang.Reflections;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;

/**
 * Affinity rules for Docker hosts.
 * <p>
 * Rules are specified as stings, formatted as follows:
 * <ul>
 * <li>(<code>SAME</code>|<code>NOT</code>)? <code>TYPE</code> <em>entityType</em>?
 * <li>(<code>SAME</code>|<code>NOT</code>)? <code>ID</code> <em>entityId</em>?
 * <li>(<code>SAME</code>|<code>NOT</code>)? <code>APPLICATION</code> <em>applicationId</em>?
 * <li><code>PREDICATE</code> <em>entityPredicateClass</em>
 * </ul>
 * The <code>SAME</code> token is the default behaviour, and means the entities must have the property defined in the rule, <code>NOT</code>
 * means they mustn't have the property. The parameter given specifies the type or id, and if it's missing thee rule will apply to the
 * properties of the entity being placed. Any rules that take a class name will instantiate an instance of that class from the current
 * classpath, so ensure the appropriate Jar files are available.
 * <p>
 * To specify a rule that there must be no entities of the same type, an entity of type SolrServer, all in the same application,
 * use these rules:
 * <pre>
 * NOT TYPE
 * TYPE brooklyn.entity.nosql.solr.SolrServer
 * SAME APPLICATION
 * </pre>
 * <p>
 * Specify the rules during configuration using the {@link #AFFINITY_RULES} key.
 * <pre>
 * 
 */
public class AffinityRules implements Predicate<Entity> {

    public static final ConfigKey<String> AFFINITY_RULES = ConfigKeys.newStringConfigKey("host.affinity.rules", "Affinity rules for entity placemnent");

    private Predicate<Entity> affinityRules = Predicates.alwaysTrue();
    private boolean allowEmpty = false;

    public static final String SAME = "SAME";
    public static final String NOT = "NOT";
    public static final String TYPE = "TYPE";
    public static final String ID = "ID";
    public static final String APPLICATION = "APPLICATION";
    public static final String PREDICATE = "PREDICATE";
    public static final String EMPTY = "EMPTY";

    private AffinityRules(String...rules) {
        this(ImmutableList.copyOf(rules));
    }

    private AffinityRules(String rules) {
        this(Splitter.on(',').omitEmptyStrings().split(rules));
    }

    private AffinityRules(Iterable<String> rules) {
        List<Predicate<Entity>> predicates = Lists.newArrayList();
        for (String rule : rules) {
            Predicate<Entity> predicate = parse(rule);
            predicates.add(predicate);
        }

        affinityRules = Predicates.and(predicates);
    }

    public static AffinityRules rules(String...rules) {
        return new AffinityRules(rules);
    }

    public static AffinityRules rules(String rules) {
        return new AffinityRules(rules);
    }

    public static AffinityRules rules(Iterable<String> rules) {
        return new AffinityRules(rules);
    }

    public Predicate<Entity> parse(String rule) {
        Preconditions.checkNotNull(rule, "rule");
        Queue<String> tokens = Queues.newArrayDeque(Splitter.on(CharMatcher.BREAKING_WHITESPACE)
                .omitEmptyStrings()
                .splitToList(rule));

        boolean same = true;
        Predicate<Entity> predicate = Predicates.alwaysTrue();

        // Check first token for special values
        String first = tokens.peek();
        if (first.equalsIgnoreCase(EMPTY)) {
            allowEmpty = true;
            tokens.remove();
            if (tokens.isEmpty()) {
                return predicate;
            } else {
                throw new IllegalStateException("Affinity rule has extra tokens: " + rule);
            }
        } else if (first.equalsIgnoreCase(SAME)) {
            same = true;
            tokens.remove();
        } else if (first.equalsIgnoreCase(NOT)) {
            same = false;
            tokens.remove();
        }

        // Check verb
        String verb = tokens.peek();
        if (verb == null) {
            throw new IllegalStateException("Affinity rule verb not specified: " + rule);
        } else {
            if (verb.equalsIgnoreCase(TYPE) || verb.equalsIgnoreCase(ID) || verb.equalsIgnoreCase(APPLICATION) || verb.equalsIgnoreCase(PREDICATE)) {
                tokens.remove();
            } else {
                throw new IllegalStateException("Affinity rule parser found unexpected verb token: " + verb);
            }
        }

        // Check paramater and instantiate if required
        final String parameter = tokens.peek();
        if (parameter == null) {
            throw new IllegalStateException("Affinity rule parameter not specified: " + rule);
        } else {
            tokens.remove();
            if (verb.equalsIgnoreCase(TYPE)) {
                predicate = new Predicate<Entity>() {
                    @Override
                    public boolean apply(@Nullable Entity input) {
                        return input.getEntityType().getName().equalsIgnoreCase(parameter);
                    }
                };
            } else if (verb.equalsIgnoreCase(ID)) {
                predicate = EntityPredicates.idEqualTo(parameter);
            } else if (verb.equalsIgnoreCase(APPLICATION)) {
                predicate = EntityPredicates.applicationIdEqualTo(parameter);
            } else if (verb.equalsIgnoreCase(PREDICATE)) {
                try {
                    Class<?> clazz = Class.forName(parameter);
                    if (Reflections.hasNoArgConstructor(clazz)) {
                        predicate = (Predicate<Entity>) Reflections.invokeConstructorWithArgs(clazz);
                    } else {
                        throw new IllegalStateException("Could not instantiate predicate: " + parameter);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Could not find predicate: " + parameter);
                }
            }
        }

        // Check for left-over tokens
        if (tokens.peek() != null) {
            throw new IllegalStateException("Affinity rile has extra tokens: " + rule);
        }

        // Create predicate and return
        if (same) {
            return predicate;
        } else {
            return Predicates.not(predicate);
        }
    }

    @Override
    public boolean apply(@Nullable Entity input) {
        return affinityRules.apply(input);
    }

    public boolean allowEmptyLocations() { return allowEmpty; }

}