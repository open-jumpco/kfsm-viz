#!/usr/bin/env bash
mkdir spec-tools
cd spec-tools
git clone https://github.com/Kotlin/kotlin-spec.git
cd kotlin-spec
chmod a+x ./gradlew
./gradlew publishToMavenLocal
cd ../
# until my changes are merged
git clone https://github.com/corneil/grammar-tools.git
# git clone https://github.com/Kotlin/grammar-tools.git
cd grammar-tools
chmod a+x ./gradlew
./gradlew publishToMavenLocal
