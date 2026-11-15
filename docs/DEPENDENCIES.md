# Dependencies and verification

Peek uses Gradle dependency verification with SHA-256 checksums committed in
`gradle/verification-metadata.xml`. A build fails if any resolved artifact does
not match the recorded checksum.

## Updating a dependency

1. Change the version in `app/build.gradle.kts` or `build.gradle.kts`.
2. Regenerate the verification metadata with the debug, release, test and lint
   configurations:

   ```bash
   ./gradlew \
     --write-verification-metadata sha256 \
     :app:assembleDebug \
     :app:assembleRelease \
     :app:testDebugUnitTest \
     :app:compileDebugAndroidTestKotlin \
     :app:lintDebug \
     --no-daemon
   ```

3. Review the diff in `gradle/verification-metadata.xml`. New entries are
   expected; unrelated checksum changes are not.
4. Run the normal CI gate locally or on the PR:

   ```bash
   ./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin lintDebug --no-daemon
   ```

The **Dependency metadata** GitHub Actions workflow can generate the same file
and a `release-dependencies.txt` inventory without committing anything. It also
runs `assembleRelease` to prove the release configuration resolves against the
committed checksums.

## Dependabot policy

- GitHub Actions patch updates are grouped.
- Gradle version updates are intentionally manual (`open-pull-requests-limit:
  0`) because every version change needs regenerated verification metadata and
  a release-config check. Dependabot security alerts still surface updates.
- Major version updates are ignored on both ecosystems.

## Release inventory

The release workflow builds from the committed metadata; it does not resolve
new artifacts outside it. The generated dependency inventory is uploaded as a
CI artifact, not attached to the public release. The public release assets stay
limited to the signed APK and `SHA256SUMS.txt`.
