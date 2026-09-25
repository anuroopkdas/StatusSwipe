#include <jni.h>
#include <string>
#include "evdev_reader.h"
#include <android/log.h>

#define LOG_TAG "EvdevJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_statusswipe_app_service_InputRootService_nativeDiscoverTouchDevice(JNIEnv *env, jobject thiz) {
    DeviceInfo info = discoverTouchDevice();
    
    if (info.path.empty()) {
        LOGE("No touch device found");
        return nullptr;
    }

    LOGI("Found touch device: %s (%s)", info.path.c_str(), info.name.c_str());
    return env->NewStringUTF(info.path.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_statusswipe_app_service_InputRootService_nativeStartReading(JNIEnv *env, jobject thiz, jstring devicePath) {
    if (!devicePath) {
        LOGE("nativeStartReading called with null devicePath");
        return;
    }

    const char *path = env->GetStringUTFChars(devicePath, nullptr);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(devicePath, path);

    JavaVM* jvm = nullptr;
    if (env->GetJavaVM(&jvm) != 0 || !jvm) {
        LOGE("Failed to get JavaVM");
        return;
    }
    
    // Create a global reference to the calling Java object to use in the callback
    jobject globalThiz = env->NewGlobalRef(thiz);
    
    jclass clazz = env->GetObjectClass(globalThiz);
    jmethodID onNativeTouchEvent = env->GetMethodID(clazz, "onNativeTouchEvent", "(IIFFJ)V");
    
    if (!onNativeTouchEvent) {
        LOGE("Could not find onNativeTouchEvent(IIFFJ)V method in InputRootService");
        env->DeleteGlobalRef(globalThiz);
        return;
    }

    LOGI("Starting evdev reader on %s...", pathStr.c_str());

    startReading(pathStr, [jvm, globalThiz, onNativeTouchEvent](const TouchEvent& te) {
        JNIEnv* currentEnv = nullptr;
        int getEnvStat = jvm->GetEnv((void**)&currentEnv, JNI_VERSION_1_6);
        bool attached = false;
        
        if (getEnvStat == JNI_EDETACHED) {
            if (jvm->AttachCurrentThread(&currentEnv, nullptr) != 0) {
                LOGE("Failed to attach thread for JNI callback");
                return;
            }
            attached = true;
        }

        if (currentEnv) {
            int actionInt = 0; // DOWN
            if (te.action == TouchAction::MOVE) actionInt = 1;
            else if (te.action == TouchAction::UP) actionInt = 2;

            currentEnv->CallVoidMethod(globalThiz, onNativeTouchEvent,
                                       te.slot, actionInt, te.normalizedX, te.normalizedY, te.timestamp);

            if (currentEnv->ExceptionCheck()) {
                currentEnv->ExceptionDescribe();
                currentEnv->ExceptionClear();
            }
        }

        if (attached) {
            jvm->DetachCurrentThread();
        }
    });

    LOGI("Finished evdev reader on %s", pathStr.c_str());
    env->DeleteGlobalRef(globalThiz);
}

extern "C" JNIEXPORT void JNICALL
Java_com_statusswipe_app_service_InputRootService_nativeStopReading(JNIEnv *env, jobject thiz) {
    LOGI("nativeStopReading called");
    stopReading();
}
