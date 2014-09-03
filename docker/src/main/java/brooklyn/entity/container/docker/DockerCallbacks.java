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
package brooklyn.entity.container.docker;

import java.util.Locale;

import brooklyn.util.text.Strings;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;

public class DockerCallbacks {

    /** Do not instantiate. */
    private DockerCallbacks() { }

    public static final String SEPARATOR = "###";
    public static final String DOCKER_HOST_CALLBACK = "docker-host-callback";
    public static final String COMMIT = "commit";
    public static final String PUSH = "push";
    public static final String IMAGE = "image";

    public static final Predicate<CharSequence> FILTER = Predicates.containsPattern(SEPARATOR + DOCKER_HOST_CALLBACK);
    public static final Splitter PARSER = Splitter.on(SEPARATOR).omitEmptyStrings();

    public static final String image() {
        return command(IMAGE);
    }

    public static final String commit() {
        return command(COMMIT);
    }

    public static final String push() {
        return command(PUSH);
    }

    private static final String command(String command, Object...rest) {
        return SEPARATOR + Joiner.on(SEPARATOR).join(DOCKER_HOST_CALLBACK, command, rest);
    }

    /** Parse and return the ID returned from a Docker command. */
    public static String checkId(String input) {
        String imageId = Strings.trim(input).toLowerCase(Locale.ENGLISH);
        if (imageId.length() == 64 && CharMatcher.anyOf("0123456789abcdef").matchesAllOf(imageId)) {
            return imageId;
        } else {
            throw new IllegalStateException("Invalid image ID returned: " + imageId);
        }
    }
}
