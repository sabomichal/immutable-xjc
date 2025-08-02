# Deployment Instructions

Please follow Portal OSSRH Staging API instructions https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuring-the-repository
Do not forget to POST the release to the staging repository as a manual step (for now):
`curl -X POST  -u xxx:xxx https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/com.github.sabomichal`