include ../make.inc

all: Create Sequential.class Featherlight.class ManualAbort.class ForkJoin.class

Create:
	mkdir -p classes 2>/dev/null
	cp *.java classes/.

Sequential.class:
	javac -cp $(JIKESRVM):. Sequential.java
	mv *.class classes/.

Featherlight.class:
	$(TRANSLATOR) Featherlight.java

ManualAbort.class:
	$(TRANSLATOR) ManualAbort.java

ForkJoin.class:
	javac -cp $(JIKESRVM):../jsr166y.jar:. ForkJoin.java
	mv *.class classes/.

clean:
	rm -rf classes logs 2>/dev/null
	
