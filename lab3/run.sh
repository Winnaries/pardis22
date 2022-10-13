#!/bin/bash
javac **/*.java
java $impl.LockFreeSkipListTest $DIST $CONTAINS $ADD $REMOVE $NTHREADS $MAX $NOPS 2> err.log
java $impl.LockFreeSkipListTest $DIST $CONTAINS $ADD $REMOVE $NTHREADS $MAX $NOPS 2> err.log
java $impl.LockFreeSkipListTest $DIST $CONTAINS $ADD $REMOVE $NTHREADS $MAX $NOPS 2> err.log