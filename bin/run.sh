#!/bin/sh
FWDIR="$(cd `dirname $0`/..; pwd)"
BUILDPATH=$FWDIR/"build.sbt"
echo $BUILDPATH
PROJECTNAME=`grep name $BUILDPATH | awk -F := '{print $2}' | sed 's/[[:blank:]]//g' | sed 's/"//g'`
PROJECTVERSION=`grep version $BUILDPATH | awk -F := '{print $2}' | sed 's/[[:blank:]]//g' | sed 's/"//g'`
SCALAVERSION=`grep scalaVersion $BUILDPATH | awk -F := '{print $2}' | sed 's/[[:blank:]]//g' | sed 's/"//g' | awk -F \. '{print $1"."$2}'`
JAR_HOME=$FWDIR/lib
JARPATH="`ls $JAR_HOME |sed "s:^:$JAR_HOME\/:"| xargs | sed  's/[[:blank:]]/:/g'`"
echo $JARPATH
JARPATH=$JARPATH":"$FWDIR"/target/scala-"$SCALAVERSION"/"$PROJECTNAME"_"$SCALAVERSION"-"$PROJECTVERSION.jar
echo "JARPATH-->"$JARPATH
JARPATH=$JARPATH":$SCALA_HOME/lib/scala-library.jar"
java -cp "$JARPATH" "$@"
#$PROJECTNAME=`grep name $BUILDPATH | awk -F := '{print $2}'`

