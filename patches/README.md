Clocker Patches
===============

This project contains files that have been changed in external projects but
are not yet available in public repositories for donload as dependencies.

## Backport from 2.0.0 for Docker JSON deserialisation patches

Backports changes from the master 2.0.0-SNAPSHOT branch of jclouds.

See [jclouds/jclouds#958](https://github.com/jclouds/jclouds/pull/958)
for full code changes.

- [`NullFilteringTypeAdapterFactories.java`](./src/main/java/org/jclouds/json/internal/NullFilteringTypeAdapterFactories.java)
