#!/bin/bash

projectcp="$1"
shift

if [ "$projectcp" == "" ]
then
	echo "project classpath is needed."
	exit 0	
fi

class="$1"
shift

if [ "$class" == "" ]
then
	echo "full classname is needed."
	exit 0
fi

java --add-opens java.base/java.lang=ALL-UNNAMED      --add-opens java.base/java.util=ALL-UNNAMED      --add-opens java.base/java.lang.reflect=ALL-UNNAMED      --add-opens java.base/java.text=ALL-UNNAMED      --add-opens java.desktop/java.awt.font=ALL-UNNAMED      -jar master/target/evosuite-master-1.2.1-SNAPSHOT.jar      -class $class      -projectCP $projectcp -Dsandbox=false -Dvirtual_net=false -Dcriterion=branch -generateMOSuite $*
