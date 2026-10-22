"""Real local Windows compatibility with the pre-JNA Credential Manager worker."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import uuid

if os.name != 'nt':
    raise SystemExit('Windows required; other platforms are not counted as a pass')
root = Path(__file__).resolve().parents[1]
cache = root / '.build-cache/windows-credential-compat'
source = cache / 'src'
classes = cache / 'classes'
source.mkdir(parents=True, exist_ok=True)
classes.mkdir(exist_ok=True)
legacy = subprocess.check_output(['git', 'show', 'fdd326b:harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java'], cwd=root)
legacy_text = legacy.decode().replace('SystemCredentials', 'LegacyWindowsCredentials')
(source / 'LegacyWindowsCredentials.java').write_bytes(legacy_text.encode())
probe = '''package io.jharness.cli;
import java.util.*;
public final class WindowsCredentialCompatibilityProbe {
  public static void main(String[] args) throws Exception {
    String target = args[0];
    if (!target.matches("J-Harness/native-compat/[a-f0-9-]{36}")) throw new IllegalArgumentException();
    if (args.length == 2 && args[1].equals("cleanup")) {
      var vault = new SystemCredentials(); vault.delete(target);
      if (!vault.read(target).isEmpty()) throw new IllegalStateException("Cleanup incomplete");
      System.out.println(Json.text(Map.of("cleanup", true))); return;
    }
    String first = "synthetic-compat-" + UUID.randomUUID();
    String second = "synthetic-compat-rotation-" + UUID.randomUUID();
    var old = new LegacyWindowsCredentials();
    var current = new SystemCredentials();
    var checks = new LinkedHashMap<String, Boolean>();
    boolean passed = false;
    try {
      old.write(target, first);
      if (!first.equals(current.read(target))) throw new IllegalStateException();
      checks.put("oldWriteNewRead", true);
      current.write(target, second);
      if (!second.equals(old.read(target))) throw new IllegalStateException();
      checks.put("newRotateOldRead", true);
      current.delete(target);
      if (!old.read(target).isEmpty()) throw new IllegalStateException();
      checks.put("newDeleteOldRead", true);
      passed = true;
    } finally {
      try { current.delete(target);
        if (!old.read(target).isEmpty()) throw new IllegalStateException();
        checks.put("cleanup", true);
      } catch (Exception failure) { passed = false; checks.put("cleanup", false); }
      System.out.println(Json.text(Map.of("passed", passed, "checks", checks)));
    }
    if (!passed) throw new IllegalStateException("Credential compatibility failed");
  }
}
'''
(source / 'WindowsCredentialCompatibilityProbe.java').write_bytes(probe.encode())
jar = root / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
bin_dir = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(bin_dir / 'javac.exe'), '-cp', str(jar), '-d', str(classes),
                str(source / 'LegacyWindowsCredentials.java'), str(source / 'WindowsCredentialCompatibilityProbe.java')],
               cwd=root, check=True, timeout=30)
target = 'J-Harness/native-compat/' + str(uuid.uuid4())
command = [str(bin_dir / 'java.exe'), '-cp', os.pathsep.join([str(classes), str(jar)]),
           'io.jharness.cli.WindowsCredentialCompatibilityProbe', target]
report = dict(passed=False)
exit_code = 1
try:
    result = subprocess.run(command, capture_output=True, text=True, cwd=root, timeout=120)
    exit_code = result.returncode
    report = json.loads(result.stdout)
except (subprocess.TimeoutExpired, ValueError):
    report['failureCode'] = 'COMPATIBILITY_UNCERTAIN'
finally:
    try:
        cleanup = subprocess.run(command + ['cleanup'], capture_output=True, text=True,
                                 cwd=root, timeout=30)
        cleaned = cleanup.returncode == 0 and json.loads(cleanup.stdout).get('cleanup') is True
    except (subprocess.TimeoutExpired, ValueError):
        cleaned = False
    report['parentCleanup'] = cleaned
    if not cleaned:
        report['passed'] = False
        report['cleanupFailureCode'] = 'CLEANUP_FAILED'
report.update(legacyCommit='fdd326b', legacySourceSha256=hashlib.sha256(legacy).hexdigest(),
              currentJarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
              fixtureSha256=hashlib.sha256(probe.encode()).hexdigest(), exitCode=exit_code)
(root / '.build-cache/R1/windows-compat.json').write_bytes((json.dumps(report, indent=2) + '\n').encode())
print(json.dumps(report))
sys.exit(0 if exit_code == 0 and report.get('passed') and report.get('parentCleanup') else 1)
