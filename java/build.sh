#!/bin/bash

# Clean up previous build files
rm -rf bin
mkdir -p bin

# 1. Compile Java source code
echo "Compiling Java source code..."
javac -cp "lib/TwsApi.jar:lib/protobuf-java-4.29.3.jar" -d bin src/IBCheck.java

# 2. Extract dependencies into the bin directory
echo "Extracting dependencies..."
cd bin
jar xf ../lib/TwsApi.jar
jar xf ../lib/protobuf-java-4.29.3.jar
# Remove signature files to prevent security exceptions during execution
rm -rf META-INF
cd ..

# 3. Create the executable JAR file
echo "Packaging IBCheck.jar..."
jar cvfe IBCheck.jar IBCheck -C bin .

echo "Build successful! Run with: java -jar IBCheck.jar"