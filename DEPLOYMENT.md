# Deployment Instructions

Artifacts are published to Maven Central through the [Central Portal](https://central.sonatype.org/publish/publish-portal-maven/)
using `central-publishing-maven-plugin`. The `central` server credentials (Portal user token) must be present in `~/.m2/settings.xml`.

1. Set `<version>` in `pom.xml` to the release version, commit as `release version X.Y.Z` and tag it `vX.Y.Z`.
2. Deploy, signing is activated by the passphrase property and the deployment is published automatically after validation:
   `mvn -B clean deploy -Dgpg.passphrase=xxx`
3. Set `<version>` back to `0-SNAPSHOT`, commit as `next development version` and push with `git push --follow-tags`.
