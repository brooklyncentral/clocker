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
package brooklyn.location.docker.strategy.affinity;

import java.util.List;
import java.util.Locale;
import java.util.Queue;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.util.javalang.Reflections;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;

/**
 * Affinity rules for Docker hosts.
 * <p>
 * Rules are specified as strings, formatted as follows:
 * <ul>
 * <li>(<code>NOT</code>) <code>TYPE</code> <em>entityType</em>?
 * <li>(<code>NOT</code>) <code>NAME</code> <em>entityName</em>
 * <li>(<code>NOT</code>) <code>ID</code> <em>entityId</em>?
 * <li>(<code>NOT</code>) <code>APPLICATION</code> <em>applicationId</em>?
 * <li>(<code>NOT</code>) <code>PREDICATE</code> <em>entityPredicateClass</em>
 * <li>(<code>NOT</code>) <code>EMPTY</code>
 * </ul>
 * The <code>SAME</code> token is the default behaviour, and means the entities must have the property defined in the rule, <code>NOT</code>
 * means they mustn't have the property. The parameter given specifies the type or id, and if it's missing thee rule will apply to the
 * properties of the entity being placed. Rules that take a class name will instantiate an instance of that class from the current
 * classpath, so ensure the appropriate Jar files are available. The <code>EMPTY</code> rule will treaty empty locations as allowable,
 * otherwise a new {@link DockerHostLocation} will be created for the container.
 * <p>
 * To specify a rule that there must be no entities of the same type, an entity of type SolrServer, all in the same application,
 * use these rules:
 * <pre>
 * NOT TYPE
 * TYPE brooklyn.entity.nosql.solr.SolrServer
 * SAME APPLICATION
 * </pre>
 * <p>
 * Specify the rules during configuration using the {@link #AFFINITY_RULES} key:
 * <pre>
 * - serviceType: brooklyn.entity.webapp.tomcat.TomcatServer
 *   brooklyn.config:
 *   affinity.rules: |
 *     NOT TYPE
 *     TYPE brooklyn.entity.nosql.solr.SolrServer
 *     SAME APPLICATION
 * </pre>
 */
public class AffinityRules implements Predicate<Entity> {

    public static final ConfigKey<String> AFFINITY_RULES = ConfigKeys.newStringConfigKey("affinity.rules", "Affinity rules for entity placemnent");

    public static final String NOT = "NOT";
    public static final String TYPE = "TYPE";
    public static final String NAME = "NAME";
    public static final String ID = "ID";
    public static final String APPLICATION = "APPLICATION";
    public static final String PREDICATE = "PREDICATE";
    public static final String EMPTY = "EMPTY";
    public static final Iterable<String> VERBS = ImmutableList.of(TYPE, NAME, ID, APPLICATION, PREDICATE, EMPTY);

    private Predicate<Entity> affinityRules = Predicates.alwaysTrue();
    private boolean allowEmpty = true;

    private final Entity entity;

    private AffinityRules(Entity entity) {
        this.entity = entity;
    }

    public static AffinityRules rulesFor(Entity entity) {
        return new AffinityRules(entity);
    }

    public AffinityRules parse(String...rules) {
        return parse(ImmutableList.copyOf(rules));
    }

    public AffinityRules parse(String rules) {
        return parse(Splitter.on(CharMatcher.anyOf("\n,")).omitEmptyStrings().split(rules));
    }

    public AffinityRules parse(Iterable<String> rules) {
        List<Predicate<Entity>> predicates = Lists.newArrayList();
        for (String rule : rules) {
            Predicate<Entity> predicate = predicate(rule);
            predicates.add(predicate);
        }

        affinityRules = Predicates.and(predicates);
        return this;
    }

    private Predicate<Entity> predicate(String rule) {
        Preconditions.checkNotNull(rule, "rule");
        Queue<String> tokens = Queues.newArrayDeque(Splitter.on(CharMatcher.WHITESPACE)
                .omitEmptyStrings()
                .splitToList(rule));

        boolean same = true;
        Predicate<Entity> predicate = Predicates.alwaysTrue();

        // Check first token for special values
        String first = tokens.peek();
        if (first.equalsIgnoreCase(NOT)) {
            same = false;
            tokens.remove();
        }

        // Check verb
        String verb = tokens.peek();
        if (verb == null) {
            throw new IllegalStateException("Affinity rule verb not specified: " + rule);
        } else {
            if (Iterables.contains(VERBS, verb.toUpperCase(Locale.ENGLISH))) {
                tokens.remove();
            } else {
                throw new IllegalStateException("Affinity rule parser found unexpected verb token: " + verb);
            }
        }

        // Check paramater and instantiate if required
        final String parameter = tokens.peek();
        if (parameter == null) {
            if (verb.equalsIgnoreCase(EMPTY)) {
                allowEmpty = same;
                tokens.remove();
                if (tokens.isEmpty()) {
                    return predicate;
                } else {
                    throw new IllegalStateException("Affinity rule has extra tokens: " + rule);
                }
            } else if (verb.equalsIgnoreCase(TYPE)) {
                predicate = new Predicate<Entity>() {
                    @Override
                    public boolean apply(@Nullable Entity input) {
                        return input.getEntityType().getName().equalsIgnoreCase(entity.getEntityType().getName()) ||
                                input.getEntityType().getSimpleName().equalsIgnoreCase(entity.getEntityType().getSimpleName());
                    }
                };
            } else if (verb.equalsIgnoreCase(ID)) {
                predicate = EntityPredicates.idEqualTo(entity.getId());
            } else if (verb.equalsIgnoreCase(APPLICATION)) {
                predicate = EntityPredicates.applicationIdEqualTo(entity.getApplicationId());
            } else {
                throw new IllegalStateException("Affinity rule parameter not specified: " + rule);
            }
        } else {
            tokens.remove();
            if (verb.equalsIgnoreCase(TYPE)) {
                predicate = new Predicate<Entity>() {
                    @Override
                    public boolean apply(@Nullable Entity input) {
                        return input.getEntityType().getName().equalsIgnoreCase(parameter) ||
                                input.getEntityType().getSimpleName().equalsIgnoreCase(parameter);
                    }
                };
            } else if (verb.equalsIgnoreCase(NAME)) {
                predicate = new Predicate<Entity>() {
                    @Override
                    public boolean apply(@Nullable Entity input) {
                        return input.getDisplayName().toLowerCase(Locale.ENGLISH).contains(parameter.toLowerCase(Locale.ENGLISH));
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
            throw new IllegalStateException("Affinity rule has extra tokens: " + rule);
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