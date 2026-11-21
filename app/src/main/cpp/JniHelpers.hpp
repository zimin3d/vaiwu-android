#pragma once

#include <jni.h>

#include "Utf8Codec.hpp"

#include <cstdint>
#include <string>
#include <string_view>

namespace vaiwu::android {

[[nodiscard]] inline jstring toJavaString(JNIEnv* env, std::string_view value)
{
    const std::u16string utf16 = utf8ToUtf16(value);
    return env->NewString(
        reinterpret_cast<const jchar*>(utf16.data()),
        static_cast<jsize>(utf16.size()));
}

[[nodiscard]] inline bool fromJavaString(JNIEnv* env, jstring input, std::string& out)
{
    if (input == nullptr) {
        return false;
    }
    const jsize length = env->GetStringLength(input);
    if (length <= 0) {
        out.clear();
        return true;
    }
    const jchar* chars = env->GetStringChars(input, nullptr);
    if (chars == nullptr) {
        return false;
    }
    out = utf16ToUtf8(reinterpret_cast<const std::uint16_t*>(chars), static_cast<std::size_t>(length));
    env->ReleaseStringChars(input, chars);
    return true;
}

} // namespace vaiwu::android
