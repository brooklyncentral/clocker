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
package brooklyn.entity.container;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;

public class DockerCallbacks {

    /** Do not instantiate. */
    private DockerCallbacks() { }

    public static final String SEPARATOR = " : ";
    public static final String DOCKER_HOST_CALLBACK = "docker-host-callback";
    public static final String COMMIT = "commit";
    public static final String PUSH = "push";
    public static final String SUBNET_ADDRESS = "subnet-address";

    public static final Predicate<CharSequence> FILTER = Predicates.containsPattern(SEPARATOR + DOCKER_HOST_CALLBACK);
    public static final Splitter PARSER = Splitter.on(SEPARATOR).omitEmptyStrings();

    public static final String commit() {
        return command(COMMIT);
    }

    public static final String push() {
        return command(PUSH);
    }

    public static final String subnetAddress() {
        return command(SUBNET_ADDRESS);
    }

    private static final String command(String command, Object...rest) {
        return SEPARATOR + Joiner.on(SEPARATOR).join(DOCKER_HOST_CALLBACK, command, rest) + SEPARATOR;
    }
}
