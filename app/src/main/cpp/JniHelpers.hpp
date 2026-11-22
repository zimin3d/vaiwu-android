#pragma once

#include <jni.h>

#include "Utf8Codec.hpp"

#include <cstdint>
#include <exception>
#include <string>
#include <string_view>

namespace vaiwu::android {

[[nodiscard]] inline jstring toJavaString(JNIEnv* env, std::string_view value) noexcept
{
    if (env == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    try {
        const std::u16string utf16 = utf8ToUtf16(value);
        return env->NewString(
            reinterpret_cast<const jchar*>(utf16.data()),
            static_cast<jsize>(utf16.size()));
    } catch (...) {
        return nullptr;
    }
}

[[nodiscard]] inline bool fromJavaString(JNIEnv* env, jstring input, std::string& out) noexcept
{
    if (env == nullptr || env->ExceptionCheck() || input == nullptr) {
        return false;
    }
    try {
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
    } catch (...) {
        return false;
    }
}

} // namespace vaiwu::android
