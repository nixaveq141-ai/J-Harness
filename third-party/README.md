# Third-party materials

J-Harness own code uses the root Apache-2.0 LICENSE. Third-party code retains its
own terms. `inventory.json` lists the exact Maven runtime and test coordinates,
artifact/POM hashes, declared license metadata, source URLs and preserved files.
Test dependency notices are included for source-build traceability; test classes
and JUnit are not part of the executable JAR.

`artifacts/` preserves every LICENSE/NOTICE/COPYING resource found in each original
dependency JAR, including Jackson's embedded numeric-parser notices. Paths are
coordinate-specific so shading cannot overwrite another dependency's notice.
`upstream/sources.json` pins supplemental official license files and their hashes.

pty4j 0.13.12 uses EPL-1.0. Its Java/native source and bundled native files are
available at https://github.com/JetBrains/pty4j/tree/b7554d649f6374183040e6554942b6d5b028b5e5
(VERSION=0.13.12); the Maven source JAR is linked in the inventory. It incorporates
ELT/JPty/JNA and distributes WinPTY and Microsoft OpenConsole/ConPTY binaries.
Their supplementary MIT licenses/notices are retained. The terminal notice is
from the pinned upstream source named in sources.json; it does not establish
the exact Microsoft source revision used to build pty4j's bundled executable.
The native build provenance remains a documented release limitation.

JNA is dual licensed LGPL-2.1-or-later OR Apache-2.0; this distribution selects
Apache-2.0 and preserves the original combined notice. SLF4J is MIT; Kotlin and
JetBrains annotations are Apache-2.0. JUnit is EPL-2.0 (test only). Refer to the
original texts for terms; POM metadata alone is not a legal-compliance proof.

Maven Wrapper scripts 3.3.4 are Apache-2.0; their headers and upstream NOTICE are
retained. The downloaded Maven distribution and build plugins are build tools,
not embedded runtime components. The application SBOM covers project dependency
graphs including tests, not every transitive dependency of every build plugin or
the operating system/JDK. OSV scanning has the same declared coverage boundary.

Rebuild the inventory with `python scripts/prepare-legal.py` after generating the
aggregate SBOM. Review changed coordinates and supplemental sources before release.
