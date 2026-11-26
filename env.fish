#!/usr/bin/env fish
# GregTech HyperCluster 开发环境（fish 版，语义与 env.sh 保持一致）。
# 用法：在仓库根目录执行  source env.fish  然后运行 ./gradlew runData / build / runGametest / runClient。

set _hypercluster_root (cd (dirname (status filename)) && pwd)

if test -x "$_hypercluster_root/.tools/jdk-17/bin/javac"
    set -gx JAVA_HOME "$_hypercluster_root/.tools/jdk-17"
else if test -z "$JAVA_HOME"; or not test -x "$JAVA_HOME/bin/javac"
    echo '请设置 JAVA_HOME 指向 Java 17 JDK，或将它解压到 .tools/jdk-17。' >&2
    set -e _hypercluster_root
    return 1
end

set _jver ("$JAVA_HOME/bin/javac" -version 2>&1)
if not string match -q 'javac 17.*' -- $_jver
    echo "此项目需要 Java 17 JDK（当前 $JAVA_HOME → $_jver）。" >&2
    set -e _hypercluster_root
    set -e _jver
    return 1
end
set -e _jver

set -gx PATH "$JAVA_HOME/bin" $PATH

if test -x "$_hypercluster_root/.tools/jdk-21/bin/javac"
    set -gx HYPERCLUSTER_JDK_21 "$_hypercluster_root/.tools/jdk-21"
end

if set -q HYPERCLUSTER_GRADLE_HOME
    set -gx GRADLE_USER_HOME "$HYPERCLUSTER_GRADLE_HOME"
else
    set -gx GRADLE_USER_HOME "$_hypercluster_root/.gradle-home"
end

set -e _hypercluster_root
