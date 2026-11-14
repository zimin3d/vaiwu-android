cmake_minimum_required(VERSION 3.22.1)
project(vaiwu_native LANGUAGES CXX)

set(VAIWU_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")
set(VAIWU_GAME_DIR "${VAIWU_ROOT}/external/vaiwu/firmware/components/game")

add_library(vaiwu_core STATIC
    "${VAIWU_GAME_DIR}/src/Assistant.cpp"
    "${VAIWU_GAME_DIR}/src/Characters.cpp"
    "${VAIWU_GAME_DIR}/src/I18n.cpp"
    "${VAIWU_GAME_DIR}/src/OfflinePhrases.cpp"
    "${VAIWU_GAME_DIR}/src/Rpg.cpp"
    "${VAIWU_GAME_DIR}/src/Shop.cpp"
    "${VAIWU_GAME_DIR}/src/SoundSynth.cpp"
    "${VAIWU_GAME_DIR}/src/VaiwuPet.cpp"
)

target_include_directories(vaiwu_core PUBLIC "${VAIWU_GAME_DIR}/include")
target_compile_features(vaiwu_core PUBLIC cxx_std_20)
set_target_properties(vaiwu_core PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(vaiwu_native SHARED native-lib.cpp)
target_link_libraries(vaiwu_native PRIVATE vaiwu_core)
target_compile_features(vaiwu_native PRIVATE cxx_std_20)
