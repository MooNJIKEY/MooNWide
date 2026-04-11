/*
 * JNI implementation for haven.botnative.HavenBotNative
 * Build: see CMakeLists.txt in this directory.
 */
#include <jni.h>

#define JMETHOD(name) Java_haven_botnative_HavenBotNative_##name

extern "C" {

JNIEXPORT jboolean JNICALL JMETHOD(nInit)(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL JMETHOD(nShutdown)(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
}

JNIEXPORT jstring JNICALL JMETHOD(nVersion)(JNIEnv* env, jclass clazz) {
    (void)clazz;
    return env->NewStringUTF("havenbot 0.1.0");
}

JNIEXPORT void JNICALL JMETHOD(nTick)(JNIEnv* env, jclass clazz, jobject gui, jdouble dt) {
    (void)env;
    (void)clazz;
    (void)gui;
    (void)dt;
    /* Future: pathfinding, planners, or push events back to Java via env->CallVoidMethod */
}

} /* extern "C" */
