#!/bin/bash

# Combine all the arguments.
printf -v var "'%s', " "$@"
var=${var%??}

# Invoke the application.
./gradlew run -PappArgs="[$var]"

