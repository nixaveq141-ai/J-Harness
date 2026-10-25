"""Fresh local R6 cases and final Java/SBOM build; no remote release promotion."""
import importlib.util
import os
from pathlib import Path

spec=importlib.util.spec_from_file_location('_r6_validation_common',Path(__file__).with_name('run-r0-validation.py'))
common=importlib.util.module_from_spec(spec)
spec.loader.exec_module(common)
common.CONTRACT=Path(__file__).with_name('contracts')/'r6-validation-cases.json'
common.JAVA_ARTIFACT=common.ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
common.JAVA_FIRST=False

def java_command():
    prefix=[str(common.ROOT/'mvnw.cmd')] if os.name=='nt' else ['sh','./mvnw']
    return prefix+['-B','-Prelease-audit','clean','verify','org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom']

common.java_command=java_command
if __name__=='__main__':raise SystemExit(common.main())
