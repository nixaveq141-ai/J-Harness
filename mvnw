#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# J-Harness verified bootstrap; fixed Maven 3.9.9, ZIP only, JDK 21+.
set -eu
scriptDir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then java="$JAVA_HOME/bin/java"; else java=java; fi
command -v "$java" >/dev/null 2>&1 || { echo 'JDK 21 java is required.' >&2; exit 1; }
mavenHome=$("$java" "$scriptDir/.mvn/wrapper/Bootstrap.java" "$scriptDir") || exit 1
unset MVNW_USERNAME MVNW_PASSWORD MVNW_REPOURL
exec "$mavenHome/bin/mvn" "$@"
