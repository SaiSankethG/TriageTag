#!/bin/sh
# Runs ./mvnw test, saves the full log to target/test.log and prints only the result lines.
./mvnw test > target/test.log 2>&1
status=$?
grep -E "Tests run:|FAIL|ERROR\]|BUILD" target/test.log | grep -v "^\s*$" | tail -25
exit $status
