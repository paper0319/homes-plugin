# HomesPlugin

Paper plugin (Minecraft 1.21.x, Java 21, Maven).

## Release process

Releases are automated via `.github/workflows/release.yml` and triggered by pushing a `v*` tag.

Steps when shipping a new version:

1. Bump `<version>` in `pom.xml` (e.g. `1.9.7` → `1.9.8`).
2. Commit the version bump and any code changes (commit message style: `Release X.Y.Z: <summary>`).
3. `git push`
4. `git tag vX.Y.Z && git push origin vX.Y.Z`

The workflow will:
- Build the shaded jar with Maven.
- Create a GitHub Release with the jar attached.
- Publish to Modrinth (project `BmLjlw32`) — loader: **paper only**, game versions: **1.21.x** (`>=1.21 <1.22`).

SpigotMC (spigotmc.org) is **not** automated — upload manually if needed.

Required GitHub secret: `MODRINTH_TOKEN`.
