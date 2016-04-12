## Fix NPE in forcePersistNow

Fixes issue where `forcePeristNow()` fails due to `persistenceRealChangeListener`
being null.

See [apache/brooklyn-server#113](https://github.com/apache/brooklyn-server/pull/113)
for full code changes.

- [`RebindManagerImpl.java`](./src/main/java/org/apache/brooklyn/core/mgmt/rebind/RebindManagerImpl.java)

This code is part of brooklyn-server _0.10.0-SNAPSHOT_ but **not** any official
Brooklyn release yet.

