#include <jni.h>

#include "JniHelpers.hpp"
#include "NativeState.hpp"
#include "vaiwu/game/Assistant.hpp"
#include "vaiwu/game/OfflinePhrases.hpp"

namespace {
NativePetState* state(jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    return reinterpret_cast<NativePetState*>(static_cast<intptr_t>(handle));
}
} // namespace

// Returns a localized offline reaction phrase for the active character and PhraseContext.
extern "C" JNIEXPORT jstring JNICALL
Java_su_neit_vaiwu_NativePet_nativePhrase(JNIEnv* env, jobject, jlong handle, jint context)
{
    try {
        if (env == nullptr || env->ExceptionCheck()) return nullptr;
        using namespace vaiwu::game;
        auto* native = state(handle);
        if (native == nullptr || context < 0 || context > static_cast<jint>(PhraseContext::Fainted)) {
            return nullptr;
        }
        const char* text = offlinePhrase(native->pet.character(),
            static_cast<PhraseContext>(context), native->rng(), Lang::Ru);
        if (text == nullptr) {
            return nullptr;
        }
        return vaiwu::android::toJavaString(env, text);
    } catch (...) {
        return nullptr;
    }
}

// Builds a CharacterProfile snapshot (traits + base RPG stats) for onboarding cards.
extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeCharacterProfile(JNIEnv* env, jobject, jlong, jint id)
{
    try {
        if (env == nullptr || env->ExceptionCheck()) return nullptr;
        using namespace vaiwu::game;
        if (id < 0 || id >= kCharacterCount) {
            return nullptr;
        }
        const auto character = static_cast<CharacterId>(id);
        const auto& traits = traitsFor(character);
        const auto& stats = baseStatsFor(character);
        const jclass model = env->FindClass("su/neit/vaiwu/CharacterProfile");
        if (model == nullptr || env->ExceptionCheck()) {
            return nullptr;
        }
        const jmethodID constructor = env->GetMethodID(model, "<init>",
            "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIII)V");
        if (constructor == nullptr || env->ExceptionCheck()) {
            env->DeleteLocalRef(model);
            return nullptr;
        }
        jstring nameStr = vaiwu::android::toJavaString(env, traits.name);
        jstring personalityStr = vaiwu::android::toJavaString(env, traits.personality);
        jstring foodStr = vaiwu::android::toJavaString(env, traits.favoriteFood);
        jobject profile = nullptr;
        if (nameStr != nullptr && personalityStr != nullptr && foodStr != nullptr && !env->ExceptionCheck()) {
            profile = env->NewObject(model, constructor, id, nameStr, personalityStr, foodStr,
                stats.str, stats.vit, stats.agi, stats.mag, stats.luck);
        }
        if (nameStr != nullptr) env->DeleteLocalRef(nameStr);
        if (personalityStr != nullptr) env->DeleteLocalRef(personalityStr);
        if (foodStr != nullptr) env->DeleteLocalRef(foodStr);
        env->DeleteLocalRef(model);
        return profile;
    } catch (...) {
        return nullptr;
    }
}

// Parses Russian chat text into a local assistant intent (alarm, timer, weather, joke, conversion).
extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeParseIntent(JNIEnv* env, jobject, jlong, jstring text)
{
    try {
        if (env == nullptr || env->ExceptionCheck()) return nullptr;
        using namespace vaiwu::game;
        std::string utf;
        if (!vaiwu::android::fromJavaString(env, text, utf)) {
            return nullptr;
        }
        const Intent parsed = parseIntent(utf.c_str(), Lang::Ru);
        const jclass model = env->FindClass("su/neit/vaiwu/AssistantIntent");
        if (model == nullptr || env->ExceptionCheck()) {
            return nullptr;
        }
        const jmethodID constructor = env->GetMethodID(model, "<init>",
            "(IIIILjava/lang/String;)V");
        if (constructor == nullptr || env->ExceptionCheck()) {
            env->DeleteLocalRef(model);
            return nullptr;
        }
        jstring convertStr = vaiwu::android::toJavaString(env, parsed.convertResult);
        jobject intent = nullptr;
        if (convertStr != nullptr && !env->ExceptionCheck()) {
            intent = env->NewObject(model, constructor, static_cast<jint>(parsed.kind), parsed.hour,
                parsed.minute, parsed.afterMinutes, convertStr);
            env->DeleteLocalRef(convertStr);
        }
        env->DeleteLocalRef(model);
        return intent;
    } catch (...) {
        return nullptr;
    }
}

// Picks a random offline Russian joke using the native pet RNG.
extern "C" JNIEXPORT jstring JNICALL
Java_su_neit_vaiwu_NativePet_nativeJoke(JNIEnv* env, jobject, jlong handle)
{
    try {
        if (env == nullptr || env->ExceptionCheck()) return nullptr;
        auto* native = state(handle);
        if (native == nullptr) {
            return vaiwu::android::toJavaString(env, "");
        }
        return vaiwu::android::toJavaString(env,
            vaiwu::game::jokeText(vaiwu::game::Lang::Ru, native->rng()));
    } catch (...) {
        return nullptr;
    }
}
