Clocker Patches
===============

This project contains files that have been changed in external projects but
are not yet available in public repositories for donload as dependencies.

# Current Patch Files

## Fix NPE in forcePersistNow

Fixes issue where `forcePeristNow()` fails due to `persistenceRealChangeListener`
being null.

See [apache/brooklyn-server#113](https://github.com/apache/brooklyn-server/pull/113)
for full code changes.

- [`RebindManagerImpl.java`](./src/main/java/org/apache/brooklyn/core/mgmt/rebind/RebindManagerImpl.java)

This code is part of brooklyn-server _0.10.0-SNAPSHOT_ but **not** any official
Brooklyn release yet.

## Add configuration option to SSH sensor to trim output

Removes surrounding whitespace which makes further transformation simpler.

See [apache/brooklyn-server#127](https://github.com/apache/brooklyn-server/pull/127)
for full code changes.

- [`SshCommandSensor.java`](./src/main/java/org/apache/brooklyn/core/sensor/ssh/SshCommandSensor.java)

## Added volumesFrom to Docker template options

This adds the `VolumesFrom` configuration to the API request to create a
container. This is the same as the `--volumes-from` command line option for
`docker run`.

See [jclouds/jclouds-labs#253](https://github.com/jclouds/jclouds-labs/pull/253)
for full code changes.

- [`DockerTemplateOptions.java`](./src/main/java/org/jclouds/docker/compute/options/DockerTemplateOptions.java)
- [`DockerComputeServiceAdapter.java`](./src/main/java/org/jclouds/docker/compute/strategy/DockerComputeServiceAdapter.java)
- [`Config.java`](./src/main/java/org/jclouds/docker/domain/Config.java)
- [`HostConfig.java`](./src/main/java/org/jclouds/docker/domain/HostConfig.java)
- [`NullSafeCopies.java`](./src/main/java/org/jclouds/docker/internal/NullSafeCopies.java)

This code is part of jclouds-labs _1.9.3-SNAPSHOT_ and _2.0.0-SNAPSHOT_
but **not** any official jclouds release yet.

## Make DockerTemplateOptions values null safe

Fixes [#276](https://github.com/brooklyncentral/clocker/issues/276) by allowing
null values in `DockerTemplateOptions` where appropriate.

See [jclouds/jclouds-labs#260](https://github.com/jclouds/jclouds-labs/pull/260)
for full code changes.

- [`DockerTemplateOptions.java`](./src/main/java/org/jclouds/docker/compute/options/DockerTemplateOptions.java)
- [`NullSafeCopies.java`](./src/main/java/org/jclouds/docker/internal/NullSafeCopies.java)

## Backport from 2.0.0 for Docker

Backports changes from the master 2.0.0-SNAPSHOT branch of the driver. Add
`OpenStdin` configuration to `DockerTemplateOptions` and set all port bindings
explicitly on container creation, as required for Docker 1.10 and above.

See [jclouds/jclouds-labs#264](https://github.com/jclouds/jclouds-labs/pull/264)
for full code changes.

- [`DockerTemplateOptions.java`](./src/main/java/org/jclouds/docker/compute/options/DockerTemplateOptions.java)
- [`DockerComputeServiceAdapter.java`](./src/main/java/org/jclouds/docker/compute/strategy/DockerComputeServiceAdapter.java)

## Backport from 2.0.0 for Docker JSON deserialisation patches

Backports changes from the master 2.0.0-SNAPSHOT branch of jclouds and jclouds-labs.

See [jclouds/jclouds#958](https://github.com/jclouds/jclouds/pull/958)
and [jclouds/jclouds-labs#269](https://github.com/jclouds/jclouds-labs/pull/269)
for full code changes.

- [`Port.java`](./src/main/java/org/jclouds/docker/domain/Port.java)
- [`NullFilteringTypeAdapterFactories.java`](./src/main/java/org/jclouds/json/internal/NullFilteringTypeAdapterFactories.java)
