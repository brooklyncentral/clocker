/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.docker.domain;

import org.jclouds.javax.annotation.Nullable;
import org.jclouds.json.SerializedNames;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Node {

   Node() {
   }

   @SerializedNames({"IP"})
   public static Node create(String ip) {
      return new AutoValue_Node(ip);
   }

   public static Builder builder() {
      return new Builder();
   }

   @Nullable
   public abstract String ip();

   public Builder toBuilder() {
      return builder().fromNode(this);
   }

   public static final class Builder {

      private String ip;

      public Builder ip(String ip) {
         this.ip = ip;
         return this;
      }

      public Node build() {
         return Node.create(this.ip);
      }

      public Builder fromNode(Node in) {
         return this.ip(in.ip());
      }
   }
}
