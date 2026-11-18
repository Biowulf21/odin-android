#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/test
javac -d build/test src/io/hiddemoens/pluskey/KeyPress.java src/io/hiddemoens/pluskey/Policy.java src/io/hiddemoens/pluskey/Options.java src/io/hiddemoens/pluskey/Mailbox.java src/io/hiddemoens/pluskey/AutomaticDebugging.java src/io/hiddemoens/pluskey/UpdatePolicy.java tests/UpdatePolicyTest.java tests/CoreTest.java tests/MailboxTest.java tests/OptionsTest.java tests/AutomaticDebuggingTest.java
java -cp build/test io.hiddemoens.pluskey.CoreTest
java -cp build/test io.hiddemoens.pluskey.MailboxTest
java -cp build/test io.hiddemoens.pluskey.OptionsTest
java -cp build/test io.hiddemoens.pluskey.AutomaticDebuggingTest

java -cp build/test io.hiddemoens.pluskey.UpdatePolicyTest
