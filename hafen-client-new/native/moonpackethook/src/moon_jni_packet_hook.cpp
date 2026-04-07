/*
 * JNI: byte-level view of HCrypt plaintext (type byte + body) for incoming/outgoing game datagrams.
 * Edit analyzeRawPacket / moonpackethook_maybe_replace below. Build: build-native.bat (Windows) or build-native.sh.
 */

#include <jni.h>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <cstdio>

#ifndef JNI_VERSION_1_8
#define JNI_VERSION_1_8 0x00010008
#endif

static volatile int g_diag_flags = 0;
/** Bit 0: fprintf one line per packet to stderr ([MoonJniNative]). */

extern "C" JNIEXPORT void JNICALL
Java_haven_MoonJniPacketHook_nSetDiag(JNIEnv*, jclass, jint flags) {
    g_diag_flags = static_cast<int>(flags);
}

/*
 * Inspect or edit packet bytes in place (same length). Called while JNI has pinned the Java array.
 * direction: 0 = incoming (after decrypt), 1 = outgoing (before encrypt).
 */
static void analyzeRawPacket(uint8_t* data, jsize len, int direction) {
    if (g_diag_flags & 1) {
        unsigned b0 = (len > 0) ? static_cast<unsigned>(data[0]) : 0u;
        std::fprintf(stderr, "[MoonJniNative] %s len=%zd type=%u\n",
            (direction == 0) ? "IN " : "OUT", static_cast<size_t>(len), b0);
    }
}

/*
 * If you need a different length, allocate with malloc and return the new buffer; set *outLen.
 * Return nullptr to keep the original buffer (possibly modified in place by analyzeRawPacket).
 * If non-null returned, this buffer is freed by JNI glue after copying to a new jbyteArray.
 */
static uint8_t* moonpackethook_maybe_replace(const uint8_t* data, jsize len, int direction, jsize* outLen) {
    (void)data;
    (void)len;
    (void)direction;
    *outLen = len;
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_haven_MoonJniPacketHook_nTransform(JNIEnv* env, jclass, jint direction, jbyteArray jdata) {
    if (!jdata)
        return nullptr;
    jsize len = env->GetArrayLength(jdata);
    if (len < 1)
        return nullptr;

    jbyte* bytes = env->GetByteArrayElements(jdata, nullptr);
    if (!bytes)
        return nullptr;

    auto* u = reinterpret_cast<uint8_t*>(bytes);
    analyzeRawPacket(u, len, static_cast<int>(direction));

    jsize newLen = len;
    uint8_t* repl = moonpackethook_maybe_replace(u, len, static_cast<int>(direction), &newLen);

    if (repl != nullptr) {
        jbyteArray out = env->NewByteArray(static_cast<jsize>(newLen));
        if (out)
            env->SetByteArrayRegion(out, 0, static_cast<jsize>(newLen), reinterpret_cast<const jbyte*>(repl));
        std::free(repl);
        env->ReleaseByteArrayElements(jdata, bytes, JNI_ABORT);
        return out;
    }

    env->ReleaseByteArrayElements(jdata, bytes, 0);
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    (void)vm;
    return JNI_VERSION_1_8;
}
