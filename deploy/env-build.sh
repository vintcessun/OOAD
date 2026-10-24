#!/usr/bin/env bash
# Source explicitly so non-interactive MCP/SSH sessions use the installed tools.
export JAVA_HOME=/root/jdk-21.0.2
export PATH="$JAVA_HOME/bin:/root/apache-maven-3.9.16/bin:$PATH"
