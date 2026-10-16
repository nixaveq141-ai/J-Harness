# J-Harness candidate installation

This is a validation candidate (0.1.0-SNAPSHOT), not a formal release.
Install Java 21 and Git, set JAVA_HOME, then extract the ZIP into a directory.
Maven is only needed to build source or to verify a Maven-based target project;
running the packaged harness does not require a Maven Wrapper in the workspace.

Windows: `jharness.cmd --version`. POSIX: `sh ./jharness --version`.
The launcher locates `lib/jharness.jar` relative to itself and preserves the caller's
working directory. Paths containing spaces are supported. No key is in the archive.

Copy docs/examples/deepseek-config.json to your own configuration, review the
endpoint/model/prices/budget, then set the API key in the named environment variable.
`jharness.cmd --config CONFIG --workspace WORKSPACE doctor` performs local checks
without contacting the model. POSIX uses `sh ./jharness` with the same arguments.
`--help` lists run/approval/cancel/resume/report and recovery commands. Start with a
dedicated workspace and review approvals; a model saying “done” is not verification.

The archive includes bom.json, the dated OSV query result, dependency-specific
third-party notices, and manifest.json with file and source hashes. Verify the ZIP
against candidate-sha256.txt from the same CI run; that unsigned checksum detects
accidental corruption and is not an independent authenticity signature.

Platform support is qualified by the actual test evidence in M9-validation.md.
Local Windows 11 and Linux container evidence predates hosted CI; macOS/other CPU
architectures require their own runner result. Bundled pty4j native architectures
do not by themselves prove support. Docker PTY is unsupported. Process cleanup
uses bounded sampling and does not guarantee cleanup of unobserved detached children.
See third-party/README.md for dependency coverage and native build provenance limits.
