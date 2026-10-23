"""Run the reviewed R2 regression case set with the common fresh-receipt collector."""
import importlib.util
import os
from pathlib import Path

spec = importlib.util.spec_from_file_location('_r2_validation_common', Path(__file__).with_name('run-r0-validation.py'))
common = importlib.util.module_from_spec(spec)
spec.loader.exec_module(common)
common.CONTRACT = Path(__file__).resolve().with_name('contracts') / 'r2-validation-cases.json'
common.JAVA_ARTIFACT = common.ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
common.JAVA_FIRST = True


def java_command():
    prefix = [str(common.ROOT / 'mvnw.cmd')] if os.name == 'nt' else ['sh', './mvnw']
    return prefix + ['-B', '-o', 'clean', 'verify']


common.java_command = java_command

if __name__ == '__main__':
    raise SystemExit(common.main())
