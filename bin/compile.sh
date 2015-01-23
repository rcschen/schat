#!/bin/sh
FWDIR="$(cd `dirname $0`/..; pwd)"
cd $FWDIR
echo `pwd`
sbt package
