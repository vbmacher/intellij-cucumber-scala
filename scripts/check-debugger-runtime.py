#!/usr/bin/env python3
"""Exercise the plugin's JDI context reader against the pinned example runtime (1 and 2 threads)."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
java_bin = Path(os.environ['JAVA_HOME']) / 'bin' if 'JAVA_HOME' in os.environ else None
java = str(java_bin / 'java') if java_bin else 'java'
javac = str(java_bin / 'javac') if java_bin else 'javac'
subprocess.run(['sbt', '--batch', 'cucumber-scala / compile', 'example / Test / compile'], cwd=root, check=True)
runtime_cp = (root / 'example/target/streams/test/fullClasspath/_global/streams/export').read_text().strip()
# The example's pinned Scala 2.13 build is also the plugin's Scala binary version.
probe_cp = str(root / 'cucumber-scala/target/scala-2.13/classes') + os.pathsep + runtime_cp
probe = root / 'cucumber-scala/src/test/resources/debugger/CucumberDebuggerProbe.java'
feature = root / 'example/src/test/resources/cucumber/examples/scalacalculator/basic_arithmetic.feature'
with tempfile.TemporaryDirectory(prefix='cucumber-debugger-probe-') as output:
    subprocess.run([javac, '-cp', probe_cp, '-d', output, str(probe)], check=True)
    for threads in ('1', '2'):
        subprocess.run([java, '-cp', output + os.pathsep + probe_cp, 'CucumberDebuggerProbe',
                        runtime_cp, str(feature), threads], cwd=root, timeout=60, check=True)
