mkdir spec-tools
cd spec-tools
git clone https://github.com/Kotlin/kotlin-spec.git
cd kotlin-spec
gradlew publishToMavenLocal
cd ../
# until my changes are merged
git clone https://github.com/corneil/grammar-tools.git
# git clone https://github.com/Kotlin/grammar-tools.git
cd grammar-tools
gradlew publishToMavenLocal
