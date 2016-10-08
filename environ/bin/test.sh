#!/bin/sh
ENVIRON__CORE___FILE="test/test-environment.edn"                           \
ENVIRON__CORE_TEST___INT=1                                                 \
ENVIRON__CORE_TEST___MAP={}                                                \
ENVIRON__CORE_TEST___VECTOR="[1 2 \"abc\"]"                                \
ENVIRON__CORE_TEST___UUID="#uuid \"10e4193c-d374-4d20-9914-b03af25a1adc\"" \
ENVIRON__CORE_TEST___SET="#{\"a set with strings\"}"                       \
ENVIRON__CORE_TEST___NUMBER_STRING=12345                                   \
lein test
