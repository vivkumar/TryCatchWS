#!/bin/bash

if [ $# -ne 1 ]
then
  echo "USAGE: ./transform.sh <input Java file>"
  exit
fi

rm out.* 2>/dev/null
rm *.log 2>/dev/null
TEMPFILE="out".$$

cd translate
java -Xss10m -cp ./JavaTryCatchWS.jar:./jksvm.jar:. JavaPrettyPrinter ../$1 &> $TEMPFILE

if [ `cat $TEMPFILE | grep Error | wc -l` -ne 0 ]
then
  echo "Errors while transforming the code..."
  mv $TEMPFILE $1.log
  echo "Please see the outfile file \""$1".log\" for more details"
  rm out.* 2>/dev/null
  exit
else
  mkdir -p ../gen
  mv $TEMPFILE ../gen/$1
  echo "Translation Done... Output file: ./gen/"$1
fi
rm out.* 2>/dev/null
