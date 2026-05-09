#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file to
# you under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Optional ENV vars
# -----------------
#   JAVA_HOME - location of a JDK home dir, required when JAVA_HOME env
#               variable is not set
#   MVNW_REPOURL - repo url base for downloading maven distribution
#   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
#   MVNW_VERBOSE - true: enable verbose log; others: silence the output
# ----------------------------------------------------------------------------

set -euf
IFS="
	"

if expr "$0" : '/.*' > /dev/null; then
  PRG="$0"
else
  PRG=$(pwd)/"$0"
fi
PRGDIR=$(dirname "$PRG")
MAVEN_PROJECTBASEDIR=$(cd "$PRGDIR" > /dev/null; pwd)

if [ -z "${MAVEN_OPTS-}" ]; then
  MAVEN_OPTS="$(grep -E '^MAVEN_OPTS=' "$MAVEN_PROJECTBASEDIR/.mvn/jvm.config" 2>/dev/null | tail -1 | sed 's/^MAVEN_OPTS=//')" || true
fi

WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
if [ -r "$WRAPPER_PROPERTIES" ]; then
  distributionUrl=$(grep -E '^distributionUrl=' "$WRAPPER_PROPERTIES" | tail -1 | sed 's/^distributionUrl=//')
  wrapperUrl=$(grep -E '^wrapperUrl=' "$WRAPPER_PROPERTIES" | tail -1 | sed 's/^wrapperUrl=//')
fi

if [ -z "${JAVA_HOME-}" ]; then
  if [ -n "${JAVA_HOME_17_X64-}" ]; then
    JAVA_HOME="$JAVA_HOME_17_X64"
  elif [ -n "${JAVA_HOME_21_X64-}" ]; then
    JAVA_HOME="$JAVA_HOME_21_X64"
  fi
fi

if [ -z "${JAVA_HOME-}" ]; then
  JAVA_PATH=$(which java 2>/dev/null) || true
  if [ -n "${JAVA_PATH-}" ]; then
    JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$JAVA_PATH" 2>/dev/null || echo "$JAVA_PATH")")")
  fi
fi

if [ -z "${JAVA_HOME-}" ]; then
  echo "Error: JAVA_HOME is not set and java could not be found." >&2
  exit 1
fi

JAVA_CMD="$JAVA_HOME/bin/java"

# Compute the distribution name from the URL
distributionName=$(basename "$distributionUrl")
distributionNameMain="${distributionName%.zip}"
distributionNameMain="${distributionNameMain%-bin}"

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

DOWNLOAD_URL="$wrapperUrl"

if [ -r "$WRAPPER_JAR" ]; then
  if [ "${MVNW_VERBOSE-}" = true ]; then
    echo "Found maven-wrapper.jar" >&2
  fi
else
  if [ "${MVNW_VERBOSE-}" = true ]; then
    echo "Downloading maven-wrapper.jar from $DOWNLOAD_URL" >&2
  fi
  if command -v wget > /dev/null; then
    if [ "${MVNW_VERBOSE-}" = true ]; then
      wget "$DOWNLOAD_URL" -O "$WRAPPER_JAR" || rm -f "$WRAPPER_JAR"
    else
      wget "$DOWNLOAD_URL" -O "$WRAPPER_JAR" -q || rm -f "$WRAPPER_JAR"
    fi
  elif command -v curl > /dev/null; then
    if [ "${MVNW_VERBOSE-}" = true ]; then
      curl -o "$WRAPPER_JAR" "$DOWNLOAD_URL" -L || rm -f "$WRAPPER_JAR"
    else
      curl -o "$WRAPPER_JAR" "$DOWNLOAD_URL" -L -s || rm -f "$WRAPPER_JAR"
    fi
  else
    echo "Error: wget or curl is required to download the maven wrapper jar." >&2
    exit 1
  fi
fi

exec "$JAVA_CMD" \
  $MAVEN_OPTS \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  ${WRAPPER_LAUNCHER} "$@"
