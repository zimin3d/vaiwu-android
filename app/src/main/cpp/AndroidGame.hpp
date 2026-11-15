#pragma once

#include "vaiwu/game/Rpg.hpp"

namespace vaiwu::android {

[[nodiscard]] inline bool applyEquipment(
    game::CharacterId character,
    game::RpgProgress& progress,
    int slot,
    int itemId)
{
    using namespace game;
    if (slot < 0 || slot >= kEquipSlotCount || itemId < -1 || itemId >= kEquipItemCount) {
        return false;
    }
    setEquipped(progress, static_cast<EquipSlot>(slot), itemId);
    normalizeRpg(character, progress);
    return progress.equipped[slot] == itemId;
}

} // namespace vaiwu::android
