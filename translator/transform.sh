#!/bin/bash

######### SET THE PATH TO JIKESRVM BUILD DIRECTORY THAT CONTAINS jksvm.jar and AJWS directory that contains AJWS.jar ########

JIKESRVM=/home/vivek/jikesrvm/JikesRVM/dist/production_ws
AJWS=/home/vivek/jikesrvm/ajws/src

################### DONT MODIFY ANYTHING BELOW THIS ###############################

if [ $# -ne 1 ]
then
  echo "USAGE: ./transform.sh <input Java file>"
  exit
fi

if [ ! -e $JIKESRVM/jksvm.jar ]
then
  echo "ERROR: FIRST SET JIKESRVM PATH IN translate.sh SCRIPT"
fi

if [ ! -e $AJWS/AJWS.jar ]
then
  echo "ERROR: FIRST SET AJWS PATH IN translate.sh SCRIPT"
fi

BASEDIR=$(dirname "$0")
INPUT=$PWD
LOGS=$INPUT/logs
GEN=$INPUT/classes
rm out.* 2>/dev/null
rm *.log 2>/dev/null
TEMPFILE="out".$$

mkdir $LOGS 2>/dev/null
mkdir $GEN 2>/dev/null
cp *.java $GEN/.
cp $BASEDIR/JavaPrettyPrinter* .
if [[ -z "${EXTRA_JARS_AJWS}" ]]; then
  java -Xss10m -cp $AJWS/AJWS.jar:$JIKESRVM/jksvm.jar:. JavaPrettyPrinter $1 &> $TEMPFILE
else
  java -Xss10m -cp $AJWS/AJWS.jar:$JIKESRVM/jksvm.jar:${EXTRA_JARS_AJWS}:. JavaPrettyPrinter $1 &> $TEMPFILE
fi

if [ `cat $TEMPFILE | grep Error | wc -l` -ne 0 ]
then
  echo "Errors while transforming the code..."
  mv $TEMPFILE $LOGS/$1.log
  echo "Please see the outfile file \""$LOGS/$1".log\" for more details"
  rm out.* 2>/dev/null
  exit
else
  mv $TEMPFILE $GEN/$1
  echo "Translation Done... Output file: "$GEN/$1
fi

rm JavaPrettyPrinter*

if [[ ! -z "${EXTRA_JARS_AJWS}" ]]; then
  # This won't be compiled as extra jars required (Dacapo)
  exit
fi

# Try Compiling the Translated File
cd $GEN
javac -cp $JIKESRVM/jksvm.jar:. $1 &> $TEMPFILE
if [ `cat $TEMPFILE | grep -i Error | wc -l` -ne 0 ]
then
  echo "Errors while compiling the code..."
  mv $TEMPFILE $LOGS/$1.log
  echo "Please see the output \""$LOGS/$1".log\" for more details"
  rm out.* 2>/dev/null
  exit
else
  echo "Compilatoin Done... Class files inside: "$GEN
fi

rm out.* 2>/dev/null

