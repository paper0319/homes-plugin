2.0.6
## Folia and Paper 26.2 Support

- Added native Folia support using Paper's Entity, Region, Global Region, and Async schedulers.
- Reworked teleports to use `teleportAsync()` and region-owned safe-location checks.
- Made TPA requests, GUIs, tab completion, warmups, shared state, and database callbacks safe for Folia's region threading model.
- Added `folia-supported: true` and publishes this version for both Paper and Folia on Modrinth.
- Compiles against the stable Paper 26.2 API while retaining `api-version: 1.21` for compatible 1.21.x servers.

## Validation

- 65 automated tests passed.
- Paper 26.2 compilation and shaded JAR packaging passed.
- The official Folia 26.2 server build is not available yet, so runtime validation used the latest compatible MockBukkit API and Paper/Folia scheduler contracts.
