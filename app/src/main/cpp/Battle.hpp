#pragma once

#include "vaiwu/game/Rpg.hpp"
#include "vaiwu/game/VaiwuPet.hpp"

#include <algorithm>
#include <cstdarg>
#include <cstdio>
#include <string>

namespace vaiwu::android {

enum class BattleOutcome : int {
    Ongoing = 0,
    Victory = 1,
    Defeat = -1,
    Escaped = 2,
};

struct BattleState {
    bool active{false};
    bool over{false};
    bool awaitingRest{false};
    bool cleared{false};
    game::DungeonRun dungeon{};
    game::Foe foe{};
    game::TurnResult lastTurn{};
    BattleOutcome outcome{BattleOutcome::Ongoing};
    int chestGold{0};
    std::string log;
};

class BattleSession {
public:
    game::ActionResult start(game::VaiwuPet& pet, bool dungeon, std::uint32_t rng)
    {
        const auto result = pet.startPlay();
        if (result != game::ActionResult::Ok) {
            return result;
        }
        state_ = BattleState{};
        state_.active = true;
        if (dungeon) {
            state_.dungeon = game::startDungeon(pet.rpg().level, rng);
            state_.foe = game::spawnDungeonFoe(state_.dungeon, pet.rpg().level, rng ^ 0x9e3779b9U);
        } else {
            state_.foe = game::spawnFoe(pet.rpg().level, rng);
        }
        state_.log = encounterLog(pet);
        return result;
    }

    void takeTurn(game::VaiwuPet& pet, game::RpgAction action, std::uint32_t rng)
    {
        if (!state_.active || state_.over) {
            return;
        }
        auto& progress = pet.rpg();
        const auto& result = state_.lastTurn = game::battleTurn(
            pet.character(), progress, state_.foe, action, rng);
        state_.log = turnLog(pet, result);

        if (result.snagged > 0) {
            pet.addCoins(static_cast<std::uint32_t>(result.snagged));
        }
        if (result.goldStolen > 0) {
            (void)pet.spendCoins(static_cast<std::uint32_t>(
                std::min(static_cast<int>(pet.coins()), result.goldStolen)));
        }
        if (result.victory) {
            pet.addCoins(static_cast<std::uint32_t>(result.goldGained));
            pet.applyPlayResult(true);
            state_.over = true;
            state_.outcome = BattleOutcome::Victory;
            if (state_.dungeon.active) {
                state_.dungeon.goldBanked += result.goldGained;
                state_.dungeon.xpBanked += result.xpGained;
                if (game::isBossRoom(state_.dungeon)) {
                    state_.chestGold = game::dungeonChestGold(state_.dungeon, rng ^ 0x85ebca6bU);
                    pet.addCoins(static_cast<std::uint32_t>(state_.chestGold));
                    state_.dungeon = game::DungeonRun{};
                    state_.cleared = true;
                } else {
                    state_.awaitingRest = true;
                }
            }
        } else if (result.defeat) {
            (void)pet.spendCoins(static_cast<std::uint32_t>(
                std::min(static_cast<int>(pet.coins()), result.goldLostHint)));
            pet.applyPlayResult(false);
            state_.over = true;
            state_.outcome = BattleOutcome::Defeat;
            state_.dungeon = game::DungeonRun{};
            state_.awaitingRest = false;
        } else if (result.escaped) {
            state_.over = true;
            state_.outcome = BattleOutcome::Escaped;
            state_.dungeon = game::DungeonRun{};
            state_.awaitingRest = false;
        }
    }

    bool nextRoom(game::VaiwuPet& pet, std::uint32_t rng)
    {
        if (!state_.active || !state_.over || !state_.awaitingRest || !state_.dungeon.active) {
            return false;
        }
        game::restBetweenRooms(pet.character(), pet.rpg());
        ++state_.dungeon.room;
        state_.foe = game::spawnDungeonFoe(state_.dungeon, pet.rpg().level, rng);
        state_.lastTurn = game::TurnResult{};
        state_.outcome = BattleOutcome::Ongoing;
        state_.over = false;
        state_.awaitingRest = false;
        state_.log = encounterLog(pet);
        return true;
    }

    void leave()
    {
        state_.active = false;
        state_.awaitingRest = false;
        state_.dungeon = game::DungeonRun{};
    }

    void allocate(game::VaiwuPet& pet, int stat)
    {
        auto& progress = pet.rpg();
        if (progress.statPoints <= 0 || stat < 0 || stat > 4) {
            return;
        }
        --progress.statPoints;
        switch (stat) {
        case 0: ++progress.alloc.str; break;
        case 1: ++progress.alloc.vit; break;
        case 2: ++progress.alloc.agi; break;
        case 3: ++progress.alloc.mag; break;
        default: ++progress.alloc.luck; break;
        }
        game::normalizeRpg(pet.character(), progress);
    }

    [[nodiscard]] const BattleState& state() const { return state_; }

private:
    static std::string text(const char* format, ...)
    {
        char buffer[256];
        va_list args;
        va_start(args, format);
        std::vsnprintf(buffer, sizeof(buffer), format, args);
        va_end(args);
        return buffer;
    }

    static game::Str monsterName(game::MonsterKind kind)
    {
        using game::MonsterKind;
        switch (kind) {
        case MonsterKind::Bat: return game::Str::MonBat;
        case MonsterKind::Shroom: return game::Str::MonShroom;
        case MonsterKind::Fox: return game::Str::MonFox;
        case MonsterKind::Ghost: return game::Str::MonGhost;
        case MonsterKind::Golem: return game::Str::MonGolem;
        default: return game::Str::MonSlime;
        }
    }

    std::string encounterLog(const game::VaiwuPet& pet) const
    {
        std::string result;
        if (state_.dungeon.active) {
            result = game::isBossRoom(state_.dungeon)
                ? game::tr(game::Lang::Ru, game::Str::DungeonBossRoom)
                : text(game::tr(game::Lang::Ru, game::Str::DungeonRoomFmt),
                    state_.dungeon.room + 1, state_.dungeon.rooms);
            result += "\n";
        }
        result += text(game::tr(game::Lang::Ru, game::Str::RpgEncounterFmt),
            game::tr(game::Lang::Ru, monsterName(state_.foe.kind)), state_.foe.level);
        (void)pet;
        return result;
    }

    static std::string turnLog(game::VaiwuPet& pet, const game::TurnResult& result)
    {
        using game::Lang;
        using game::Str;
        (void)pet;
        std::string first;
        std::string second;
        if (result.playerStunned) {
            first = game::tr(Lang::Ru, Str::RpgYouStunned);
        } else if (result.noMp) {
            first = game::tr(Lang::Ru, Str::RpgNoMp);
        } else if (result.noPotion) {
            first = game::tr(Lang::Ru, Str::RpgNoPotion);
        } else if (result.escaped) {
            first = game::tr(Lang::Ru, Str::RpgRunOk);
        } else if (result.mpRestored > 0) {
            first = text(game::tr(Lang::Ru, Str::RpgMpRestoredFmt), result.mpRestored);
        } else if (result.healed > 0) {
            first = text(game::tr(Lang::Ru, Str::RpgHealedFmt), result.healed);
        } else if (result.runFailed) {
            first = game::tr(Lang::Ru, Str::RpgRunFail);
        } else if (result.skillUsed && result.playerDmg == 0 && result.drained > 0) {
            first = text(game::tr(Lang::Ru, Str::RpgHealedFmt), result.drained);
        } else {
            first = text(game::tr(Lang::Ru, Str::RpgYouHitFmt), result.playerDmg);
            if (result.playerCrit) {
                first += game::tr(Lang::Ru, Str::RpgCrit);
            }
            if (result.extraDmg > 0) {
                first += text(game::tr(Lang::Ru, Str::RpgSecondHitFmt), result.extraDmg);
            }
            if (result.drained > 0) {
                first += text(game::tr(Lang::Ru, Str::RpgDrainFmt), result.drained);
            }
            if (result.guarded > 0) {
                first += game::tr(Lang::Ru, Str::RpgGuardUp);
            }
            if (result.snagged > 0) {
                first += text(game::tr(Lang::Ru, Str::RpgSnagFmt), result.snagged);
            }
            if (result.foePoisoned) {
                first += game::tr(Lang::Ru, Str::RpgFoePoisoned);
            }
        }
        if (result.victory) {
            second = text(game::tr(Lang::Ru, Str::RpgWinFmt), result.xpGained, result.goldGained);
        } else if (result.defeat) {
            second = text(game::tr(Lang::Ru, Str::RpgDefeatFmt), result.goldLostHint);
        } else if (result.foeMissed) {
            second = game::tr(Lang::Ru, Str::RpgFoeMiss);
        } else if (result.foeDmg > 0) {
            second = text(game::tr(Lang::Ru, Str::RpgFoeHitFmt), result.foeDmg);
        }
        if (result.poisoned) second += game::tr(Lang::Ru, Str::RpgYouPoisoned);
        if (result.poisonDmg > 0) second += text(game::tr(Lang::Ru, Str::RpgPoisonTickFmt), result.poisonDmg);
        if (result.foePoisonDmg > 0) second += text(game::tr(Lang::Ru, Str::RpgFoePoisonTickFmt), result.foePoisonDmg);
        if (result.foeRegen > 0) second += text(game::tr(Lang::Ru, Str::RpgFoeRegenFmt), result.foeRegen);
        if (result.goldStolen > 0) second += text(game::tr(Lang::Ru, Str::RpgFoeStealFmt), result.goldStolen);
        return second.empty() ? first : first + "\n" + second;
    }

    BattleState state_{};
};

} // namespace vaiwu::android
