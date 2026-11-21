#include <jni.h>

#include <cstdint>

#include "vaiwu/game/SoundSynth.hpp"

namespace {

// Sfx is a small closed enum, so rejecting anything outside it keeps a bad
// Kotlin id from reaching an out-of-range switch in the synth.
constexpr int kSfxCount = static_cast<int>(vaiwu::game::Sfx::Encounter) + 1;
constexpr jint kMinSampleRateHz = 8000;
constexpr jint kMaxSampleRateHz = 96000;

} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_su_neit_vaiwu_SoundSynth_nativeRenderSfx(JNIEnv* env, jobject, jint effect, jint sampleRate)
{
    if (effect < 0 || effect >= kSfxCount || sampleRate < kMinSampleRateHz || sampleRate > kMaxSampleRateHz) {
        return nullptr;
    }
    const auto pcm = vaiwu::game::renderSfx(
        static_cast<vaiwu::game::Sfx>(effect), static_cast<std::uint32_t>(sampleRate));
    if (pcm.empty()) {
        return nullptr;
    }
    jbyteArray out = env->NewByteArray(static_cast<jsize>(pcm.size()));
    if (out == nullptr) {
        return nullptr;
    }
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(pcm.size()),
        reinterpret_cast<const jbyte*>(pcm.data()));
    return out;
}
