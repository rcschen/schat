#!/bin/sh
FWDIR="$(cd `dirname $0`/..; pwd)"
BUILDPATH=$FWDIR/"build.sbt"
echo $BUILDPATH
PROJECTNAME=`grep name $BUILDPATH | awk -F := '{print $2}'`
PROJECTVERSION=`grep version $BUILDPATH | awk -F := '{print $2}'`
SCALAVERSION=`grep scalaVersion $BUILDPATH | awk -F := '{print $2}'`
echo "---->"$PROJECTNAME
echo "---->"$PROJECTVERSION
echo "---->"$SCALAVERSION
export JAR_HOME=$FWDIR/lib
JARPATH=`ls ../lib/ |sed "s:^:$JAR_HOME\/:"|xargs | sed  's/[[:blank:]]/:/g'`
echo "?????"$PROJECTNAME\_$SCALAVERSION-$PROJECTVERSION
JARPATH=$JARPATH:$FWDIR/target/scala-$SCALAVERSION/$PROJECTNAME\_$SCALAVERSION-$PROJECTVERSION.jar
echo "JARPATH-->"$JARPATH
JARPATH=$JARPATH":$SCALA_HOME/lib/scala-library.jar"
java -cp "$JARPATH" "$@"
