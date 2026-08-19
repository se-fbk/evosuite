#!/bin/bash

# Note: on JDK 18+, -Djava.security.manager=allow (below) is required for
# EvoSuite's sandbox to actually isolate SUT code during generation. The same
# flag is needed again whenever you later compile/run the *generated* test
# suite on JDK 18+, if you want its sandbox scaffolding to work too.
#
# The --add-opens list here matches the one EvoSuite.java adds for the forked
# client process (see master/src/main/java/org/evosuite/EvoSuite.java). Keep
# them in sync: with -Dclient_on_thread=true (no fork - useful for debugging)
# the client runs in *this* JVM, so it only gets whatever flags were passed
# here - it does not benefit from EvoSuite.java's own list. Note that
# -Dclient_on_thread=true does NOT avoid RMI - master/client communication
# still goes through RMI stubs even within a single JVM - hence the RMI
# codebase flags below are needed here too, not just in EvoSuite.java.

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

# RMI codebase for this JVM's own classpath, so that whichever end of an RMI
# call is deserializing (this JVM acting as master, or as client in
# -Dclient_on_thread=true mode) can fetch classes referenced in received
# objects but not on its own classpath, instead of failing outright. See the
# corresponding fix in EvoSuite.java (used for the normal, forked-client case)
# for the full rationale. Safe here since this is all localhost traffic
# between processes EvoSuite itself started.
codebase=""
IFS=':' read -ra cpentries <<< "$projectcp"
for entry in "${cpentries[@]}"; do
	[ -z "$entry" ] && continue
	url="file://$(readlink -f "$entry")"
	[ -d "$entry" ] && url="$url/"
	codebase="$codebase $url"
done

java --add-opens java.base/java.lang=ALL-UNNAMED      --add-opens java.base/java.util=ALL-UNNAMED      --add-opens java.base/java.lang.reflect=ALL-UNNAMED      --add-opens java.base/java.io=ALL-UNNAMED      --add-opens java.base/java.text=ALL-UNNAMED      --add-opens java.desktop/java.awt=ALL-UNNAMED      --add-opens java.desktop/java.awt.font=ALL-UNNAMED      --add-opens java.desktop/javax.swing=ALL-UNNAMED      --add-opens java.desktop/sun.awt=ALL-UNNAMED      --add-opens java.desktop/sun.font=ALL-UNNAMED      -Djava.security.manager=allow      -Djava.rmi.server.useCodebaseOnly=false      "-Djava.rmi.server.codebase=$codebase"      -jar master/target/evosuite-master-1.2.1-SNAPSHOT.jar      -class $class      -projectCP $projectcp -Dsandbox=false -Dvirtual_net=false -Dcriterion=branch -generateMOSuite $*
