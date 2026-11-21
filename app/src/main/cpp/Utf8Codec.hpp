#pragma once

#include <cstdint>
#include <string>
#include <string_view>

namespace vaiwu::android {

// Converts standard UTF-8 into UTF-16 code units (with surrogate pairs for
// supplementary-plane characters like emoji). Invalid byte sequences are
// replaced with U+FFFD so JNI's NewString never sees malformed data and never
// falls back to NewStringUTF's Modified UTF-8 restrictions.
[[nodiscard]] inline std::u16string utf8ToUtf16(std::string_view input)
{
    std::u16string out;
    out.reserve(input.size());

    auto appendCodePoint = [&out](std::uint32_t cp) {
        if (cp <= 0xFFFFU) {
            out.push_back(static_cast<char16_t>(cp));
        } else {
            cp -= 0x10000U;
            out.push_back(static_cast<char16_t>(0xD800U + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00U + (cp & 0x3FFU)));
        }
    };

    std::size_t i = 0;
    const std::size_t n = input.size();
    while (i < n) {
        const auto b0 = static_cast<std::uint8_t>(input[i]);
        if (b0 < 0x80U) {
            out.push_back(static_cast<char16_t>(b0));
            ++i;
            continue;
        }
        if ((b0 & 0xE0U) == 0xC0U) {
            if (i + 1 < n) {
                const auto b1 = static_cast<std::uint8_t>(input[i + 1]);
                if ((b1 & 0xC0U) == 0x80U) {
                    const std::uint32_t cp =
                        (static_cast<std::uint32_t>(b0 & 0x1FU) << 6) | (b1 & 0x3FU);
                    if (cp >= 0x80U) {
                        appendCodePoint(cp);
                        i += 2;
                        continue;
                    }
                }
            }
            out.push_back(u'\uFFFD');
            ++i;
            continue;
        }
        if ((b0 & 0xF0U) == 0xE0U) {
            if (i + 2 < n) {
                const auto b1 = static_cast<std::uint8_t>(input[i + 1]);
                const auto b2 = static_cast<std::uint8_t>(input[i + 2]);
                if ((b1 & 0xC0U) == 0x80U && (b2 & 0xC0U) == 0x80U) {
                    const std::uint32_t cp =
                        (static_cast<std::uint32_t>(b0 & 0x0FU) << 12)
                        | (static_cast<std::uint32_t>(b1 & 0x3FU) << 6)
                        | (b2 & 0x3FU);
                    if (cp >= 0x800U && (cp < 0xD800U || cp > 0xDFFFU)) {
                        appendCodePoint(cp);
                        i += 3;
                        continue;
                    }
                }
            }
            out.push_back(u'\uFFFD');
            ++i;
            continue;
        }
        if ((b0 & 0xF8U) == 0xF0U) {
            if (i + 3 < n) {
                const auto b1 = static_cast<std::uint8_t>(input[i + 1]);
                const auto b2 = static_cast<std::uint8_t>(input[i + 2]);
                const auto b3 = static_cast<std::uint8_t>(input[i + 3]);
                if ((b1 & 0xC0U) == 0x80U && (b2 & 0xC0U) == 0x80U && (b3 & 0xC0U) == 0x80U) {
                    const std::uint32_t cp =
                        (static_cast<std::uint32_t>(b0 & 0x07U) << 18)
                        | (static_cast<std::uint32_t>(b1 & 0x3FU) << 12)
                        | (static_cast<std::uint32_t>(b2 & 0x3FU) << 6)
                        | (b3 & 0x3FU);
                    if (cp >= 0x10000U && cp <= 0x10FFFFU) {
                        appendCodePoint(cp);
                        i += 4;
                        continue;
                    }
                }
            }
            out.push_back(u'\uFFFD');
            ++i;
            continue;
        }
        out.push_back(u'\uFFFD');
        ++i;
    }
    return out;
}

// Converts Java UTF-16 code units into standard UTF-8. Surrogate pairs become
// 4-byte UTF-8 sequences; unpaired surrogates become U+FFFD.
[[nodiscard]] inline std::string utf16ToUtf8(const std::uint16_t* units, std::size_t length)
{
    std::string out;
    if (units == nullptr || length == 0U) {
        return out;
    }
    out.reserve(length);

    auto appendUtf8 = [&out](std::uint32_t cp) {
        if (cp < 0x80U) {
            out.push_back(static_cast<char>(cp));
        } else if (cp < 0x800U) {
            out.push_back(static_cast<char>(0xC0U | (cp >> 6)));
            out.push_back(static_cast<char>(0x80U | (cp & 0x3FU)));
        } else if (cp < 0x10000U) {
            out.push_back(static_cast<char>(0xE0U | (cp >> 12)));
            out.push_back(static_cast<char>(0x80U | ((cp >> 6) & 0x3FU)));
            out.push_back(static_cast<char>(0x80U | (cp & 0x3FU)));
        } else {
            out.push_back(static_cast<char>(0xF0U | (cp >> 18)));
            out.push_back(static_cast<char>(0x80U | ((cp >> 12) & 0x3FU)));
            out.push_back(static_cast<char>(0x80U | ((cp >> 6) & 0x3FU)));
            out.push_back(static_cast<char>(0x80U | (cp & 0x3FU)));
        }
    };

    for (std::size_t i = 0; i < length; ++i) {
        const std::uint32_t unit = units[i];
        if (unit >= 0xD800U && unit <= 0xDBFFU) {
            if (i + 1 < length) {
                const std::uint32_t low = units[i + 1];
                if (low >= 0xDC00U && low <= 0xDFFFU) {
                    const std::uint32_t cp =
                        0x10000U + ((unit - 0xD800U) << 10) + (low - 0xDC00U);
                    appendUtf8(cp);
                    ++i;
                    continue;
                }
            }
            appendUtf8(0xFFFDU);
        } else if (unit >= 0xDC00U && unit <= 0xDFFFU) {
            appendUtf8(0xFFFDU);
        } else {
            appendUtf8(unit);
        }
    }
    return out;
}

} // namespace vaiwu::android
