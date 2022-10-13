#!/bin/bash
javac **/*.java
java $IMPL.LockFreeSkipListTest $DIST $CONTAINS $ADD $REMOVE $NTHREADS $MAX $NOPS 2> err.log
java $IMPL.LockFreeSkipListTest $DIST $CONTAINS $ADD $REMOVE $NTHREADS $MAX $NOPS 2> err.log
java $IMPL.LockFreeSkipListTest $DIST $CONTAINS $ADD $REMOVE $NTHREADS $MAX $NOPS 2> err.log