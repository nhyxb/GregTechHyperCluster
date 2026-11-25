#!/usr/bin/env bash

_hypercluster_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ -x "$_hypercluster_root/.tools/jdk-17/bin/javac" ]]; then
    export JAVA_HOME="$_hypercluster_root/.tools/jdk-17"
elif [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/javac" ]]; then
    echo '请设置 JAVA_HOME 指向 Java 17 JDK，或将它解压到 .tools/jdk-17。' >&2
    unset _hypercluster_root
    return 1
fi

if [[ "$("$JAVA_HOME/bin/javac" -version 2>&1)" != 'javac 17.'* ]]; then
    echo '此项目需要 Java 17 JDK。' >&2
    unset _hypercluster_root
    return 1
fi

export PATH="$JAVA_HOME/bin:$PATH"
if [[ -x "$_hypercluster_root/.tools/jdk-21/bin/javac" ]]; then
    export HYPERCLUSTER_JDK_21="$_hypercluster_root/.tools/jdk-21"
fi
export GRADLE_USER_HOME="${HYPERCLUSTER_GRADLE_HOME:-$_hypercluster_root/.gradle-home}"
unset _hypercluster_root
