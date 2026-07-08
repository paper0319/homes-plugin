2.0.1
## Compatibility Fixes

- Fixed plugin loading on Paper 1.21.11 by declaring the Bukkit API version as `1.21` instead of the unsupported `26.2` value.
- Kept release metadata for Paper/Minecraft 26.x while compiling against the lowest supported API line for wider runtime compatibility.

## Build/Test

- Aligned the Paper API and MockBukkit test dependencies with the 1.21 compatibility baseline.
