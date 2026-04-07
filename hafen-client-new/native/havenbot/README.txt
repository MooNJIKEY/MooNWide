havenbot — JNI bridge (C++ ↔ Java haven.botnative.HavenBotNative)

Build (Windows): set JAVA_HOME, run build-native.bat. DLL is copied to out\havenbot.dll.
Build (Linux/macOS): chmod +x build-native.sh && ./build-native.sh

Run the game: bin\env.bat adds -Djava.library.path=... so havenbot.dll can live in
hafen-client-new\bin next to hafen.jar OR in native\havenbot\out.

Override: -Dhaven.botnative.lib=C:\path\to\havenbot.dll

After changing Java native methods, regenerate the JNI header:
  javac -h include -d build/tmp -classpath <hafen classes> src/haven/botnative/HavenBotNative.java
Then update src/haven_bot.cpp symbol names (Java_haven_botnative_HavenBotNative_*)
