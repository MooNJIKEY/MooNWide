moonpackethook — JNI wire hook (HCrypt plaintext: type byte + body)

Java: haven.MoonJniPacketHook — called from Connection.Crypto after decrypt / before encrypt.
Enable at runtime: -Dhaven.moonjni.wire=true (library must load or hook is skipped).

Build (Windows): set JAVA_HOME, run build-native.bat → out/moonpackethook.dll
Build (Linux/macOS): chmod +x build-native.sh && ./build-native.sh

Run: bin/env.bat adds native/havenbot/out to java.library.path; moonpackethook is also on path via:
  %HAFEN%\native\moonpackethook\out

Override DLL path: -Dhaven.moonjni.lib=C:\path\to\moonpackethook.dll

Edit C++: native/moonpackethook/src/moon_jni_packet_hook.cpp
  - analyzeRawPacket() — inspect / patch bytes in place (same length)
  - moonpackethook_maybe_replace() — return malloc'd buffer to substitute entire packet (any length)

Regenerate JNI header after changing Java natives:
  javac -h native/moonpackethook/include -d build/tmp -cp build/classes src/haven/MoonJniPacketHook.java

Note: changing lengths or corrupting crypto/plaintext can disconnect the session. OBJDELTA and map
streams are not this path — only MSG_CRYPT payload after session crypto.
