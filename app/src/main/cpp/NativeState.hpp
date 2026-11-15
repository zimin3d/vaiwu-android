#pragma once

#include "Battle.hpp"

#include <random>

struct NativePetState {
    vaiwu::game::VaiwuPet pet;
    bool rpsStarted{false};
    std::mt19937 rng{std::random_device{}()};
    vaiwu::android::BattleSession battle;
};
