#include <jni.h>

#include <cstdint>
#include <string>

#include "vaiwu/game/Characters.hpp"
#include "vaiwu/game/VaiwuPet.hpp"

namespace {

vaiwu::game::VaiwuPet* pet(jlong handle)
{
    return reinterpret_cast<vaiwu::game::VaiwuPet*>(static_cast<intptr_t>(handle));
}

jstring javaString(JNIEnv* env, const std::string& value)
{
    return env->NewStringUTF(value.c_str());
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_su_neit_vaiwu_NativePet_nativeCreate(JNIEnv*, jobject)
{
    return static_cast<jlong>(reinterpret_cast<intptr_t>(new vaiwu::game::VaiwuPet()));
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeDestroy(JNIEnv*, jobject, jlong handle)
{
    delete pet(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeStartNew(
    JNIEnv* env, jobject, jlong handle, jint characterId, jstring name, jlong nowMs)
{
    const char* utfName = env->GetStringUTFChars(name, nullptr);
    const std::string petName = utfName == nullptr ? "" : utfName;
    if (utfName != nullptr) {
        env->ReleaseStringUTFChars(name, utfName);
    }
    pet(handle)->startNew(static_cast<vaiwu::game::CharacterId>(characterId), petName,
        static_cast<std::uint64_t>(nowMs));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeLoad(
    JNIEnv* env, jobject, jlong handle, jstring data, jlong nowMs)
{
    const char* utfData = env->GetStringUTFChars(data, nullptr);
    if (utfData == nullptr) {
        return JNI_FALSE;
    }
    const bool loaded = pet(handle)->deserialize(utfData, static_cast<std::uint64_t>(nowMs));
    env->ReleaseStringUTFChars(data, utfData);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_su_neit_vaiwu_NativePet_nativeSave(JNIEnv* env, jobject, jlong handle)
{
    return javaString(env, pet(handle)->serialize());
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeTick(JNIEnv*, jobject, jlong handle, jlong nowMs)
{
    pet(handle)->tick(static_cast<std::uint64_t>(nowMs));
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeAction(JNIEnv*, jobject, jlong handle, jint action)
{
    using vaiwu::game::ActionResult;
    auto* p = pet(handle);
    switch (action) {
    case 0:
        return static_cast<jint>(p->feed());
    case 1: {
        const ActionResult result = p->startPlay();
        if (result == ActionResult::Ok) {
            p->applyPlayResult(true);
        }
        return static_cast<jint>(result);
    }
    case 2:
        return static_cast<jint>(p->wash());
    case 3:
        return static_cast<jint>(p->toilet());
    case 4:
        return static_cast<jint>(p->talk());
    case 5:
        p->wake();
        return 0;
    case 6:
        p->cure();
        return 0;
    case 7:
        p->giveAffection(6.0f);
        return 0;
    default:
        return -1;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeSnapshot(JNIEnv* env, jobject, jlong handle)
{
    using vaiwu::game::traitsFor;
    const auto* p = pet(handle);
    const auto& stats = p->stats();
    const auto& traits = traitsFor(p->character());

    const jclass snapshotClass = env->FindClass("su/neit/vaiwu/PetSnapshot");
    const jmethodID constructor = env->GetMethodID(snapshotClass, "<init>",
        "(ILjava/lang/String;Ljava/lang/String;FFFFFIIIIZ)V");

    return env->NewObject(snapshotClass, constructor,
        static_cast<jint>(p->character()),
        javaString(env, traits.name),
        javaString(env, p->name()),
        static_cast<jfloat>(stats.hunger),
        static_cast<jfloat>(stats.happiness),
        static_cast<jfloat>(stats.energy),
        static_cast<jfloat>(stats.discipline),
        static_cast<jfloat>(stats.weightKg),
        static_cast<jint>(stats.ageSeconds),
        static_cast<jint>(stats.coins),
        static_cast<jint>(p->emotion()),
        static_cast<jint>(p->life()),
        p->isSick() ? JNI_TRUE : JNI_FALSE);
}
