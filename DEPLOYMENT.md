# Deployment Instructions

Artifacts are published to Maven Central through the [Central Portal](https://central.sonatype.org/publish/publish-portal-maven/)
using `central-publishing-maven-plugin`. The `central` server credentials (Portal user token) and `gpg.passphrase`
must be present in `~/.m2/settings.xml`.

The version in `pom.xml` is `0-SNAPSHOT` between releases, so the release version has to be bumped by hand.
Pick the next version after the latest [release](https://github.com/sabomichal/immutable-xjc/releases) following
semantic versioning:

* patch (`2.0.7` → `2.0.8`): bug fixes and dependency updates
* minor (`2.0.8` → `2.1.0`): new options or features, backwards compatible
* major (`2.1.0` → `3.0.0`): breaking changes, e.g. a new JAXB major version or a raised minimum Java version

## Release steps

1. Set `<version>` in `pom.xml` to the release version, commit and create an annotated tag
   (a lightweight tag is not pushed by `--follow-tags`):
   ```shell
   git commit -am "release version X.Y.Z"
   git tag -a vX.Y.Z -m "vX.Y.Z"
   ```
2. Deploy. The `sign` profile signs the artifacts, and the deployment is published automatically after validation.
   The profile has to be enabled explicitly, `gpg.passphrase` from `settings.xml` does not activate it.
   On Windows point the plugin to the GnuPG installation holding the key, the `gpg` bundled with Git Bash has an empty keyring:
   ```shell
   mvn -B clean deploy -Psign -Dgpg.executable=C:/bin/GnuPG/bin/gpg.exe
   ```
3. Set `<version>` back to `0-SNAPSHOT`, commit and push the commits together with the tag:
   ```shell
   git commit -am "next development version"
   git push --follow-tags origin master
   ```
4. Create a [GitHub release](https://github.com/sabomichal/immutable-xjc/releases/new) for the tag `vX.Y.Z` with release notes.
