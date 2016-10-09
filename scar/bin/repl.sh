#!/bin/sh
SCAR__CORE___FILE="test/test-environment.edn"                           \
SCAR__CORE_TEST___INT=1                                                 \
SCAR__CORE_TEST___MAP={}                                                \
SCAR__CORE_TEST___VECTOR="[1 2 \"abc\"]"                                \
SCAR__CORE_TEST___UUID="#uuid \"10e4193c-d374-4d20-9914-b03af25a1adc\"" \
SCAR__CORE_TEST___SET="#{\"a set with strings\"}"                       \
SCAR__CORE_TEST___NUMBER_STRING=12345                                   \
lein with-profile +test repl
