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
