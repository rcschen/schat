#!/bin/sh
FWDIR="$(cd `dirname $0`/..; pwd)"
echo $FWDIR
export JAR_HOME=$FWDIR/lib
echo $JAR_HOME
JARPATH="$(echo `ls ../lib/ |sed "s:^:$JAR_HOME\/:"|xargs | sed  's/[[:blank:]]/:/g'`)"
echo $JARPATH
JARPATH=$JARPATH":"$FWDIR"/target/scala-2.11/schat_2.11-0.0.0.jar"
JARPATH=$JARPATH":/root/scala-2.11.1/lib/scala-library.jar"

echo "$@"
echo "$JARPATH"
java -cp "$JARPATH" "$@"
