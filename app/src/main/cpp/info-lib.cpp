#include <jni.h>

#include "NativeState.hpp"
#include "vaiwu/game/Assistant.hpp"
#include "vaiwu/game/OfflinePhrases.hpp"

namespace {
NativePetState* state(jlong handle)
{
    return reinterpret_cast<NativePetState*>(static_cast<intptr_t>(handle));
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_su_neit_vaiwu_NativePet_nativePhrase(JNIEnv* env, jobject, jlong handle, jint context)
{
    using namespace vaiwu::game;
    auto* native = state(handle);
    if (context < 0 || context > static_cast<jint>(PhraseContext::Fainted)) {
        return nullptr;
    }
    return env->NewStringUTF(offlinePhrase(native->pet.character(),
        static_cast<PhraseContext>(context), native->rng(), Lang::Ru));
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeCharacterProfile(JNIEnv* env, jobject, jlong, jint id)
{
    using namespace vaiwu::game;
    if (id < 0 || id >= kCharacterCount) {
        return nullptr;
    }
    const auto character = static_cast<CharacterId>(id);
    const auto& traits = traitsFor(character);
    const auto& stats = baseStatsFor(character);
    const jclass model = env->FindClass("su/neit/vaiwu/CharacterProfile");
    const jmethodID constructor = env->GetMethodID(model, "<init>",
        "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIII)V");
    return env->NewObject(model, constructor, id, env->NewStringUTF(traits.name),
        env->NewStringUTF(traits.personality), env->NewStringUTF(traits.favoriteFood),
        stats.str, stats.vit, stats.agi, stats.mag, stats.luck);
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeParseIntent(JNIEnv* env, jobject, jlong, jstring text)
{
    using namespace vaiwu::game;
    const char* utf = env->GetStringUTFChars(text, nullptr);
    if (utf == nullptr) {
        return nullptr;
    }
    const Intent parsed = parseIntent(utf, Lang::Ru);
    env->ReleaseStringUTFChars(text, utf);
    const jclass model = env->FindClass("su/neit/vaiwu/AssistantIntent");
    const jmethodID constructor = env->GetMethodID(model, "<init>",
        "(IIIILjava/lang/String;)V");
    return env->NewObject(model, constructor, static_cast<jint>(parsed.kind), parsed.hour,
        parsed.minute, parsed.afterMinutes, env->NewStringUTF(parsed.convertResult));
}

extern "C" JNIEXPORT jstring JNICALL
Java_su_neit_vaiwu_NativePet_nativeJoke(JNIEnv* env, jobject, jlong handle)
{
    auto* native = state(handle);
    return env->NewStringUTF(vaiwu::game::jokeText(vaiwu::game::Lang::Ru, native->rng()));
}
