#include "AndroidGame.hpp"
#include "Battle.hpp"
#include "vaiwu/game/Assistant.hpp"
#include "vaiwu/game/OfflinePhrases.hpp"

#include <cstdio>
#include <cstring>

using vaiwu::android::applyEquipment;
using namespace vaiwu::game;

namespace {

int failures = 0;

#define CHECK(cond)                                                              \
    do {                                                                         \
        if (!(cond)) {                                                           \
            std::printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);          \
            ++failures;                                                          \
        }                                                                        \
    } while (0)

void testRejectedEquipmentReturnsFalse()
{
    RpgProgress progress;
    normalizeRpg(CharacterId::Rin, progress);

    CHECK(!applyEquipment(CharacterId::Rin, progress, -1, -1));
    CHECK(!applyEquipment(CharacterId::Rin, progress, kEquipSlotCount, -1));
    CHECK(!applyEquipment(CharacterId::Rin, progress, 0, -2));
    CHECK(!applyEquipment(CharacterId::Rin, progress, 0, kEquipItemCount));
    CHECK(!applyEquipment(CharacterId::Rin, progress, 0,
        static_cast<int>(EquipItem::SteelSword)));
    CHECK(progress.equipped[0] == -1);

    grantEquip(progress, EquipItem::SteelSword);
    CHECK(!applyEquipment(CharacterId::Rin, progress, 1,
        static_cast<int>(EquipItem::SteelSword)));
    CHECK(progress.equipped[0] == static_cast<int>(EquipItem::SteelSword));
    CHECK(progress.equipped[1] == -1);

    CHECK(!applyEquipment(CharacterId::Rin, progress, 0,
        static_cast<int>(EquipItem::MagicStaff)));
    CHECK(progress.equipped[0] == static_cast<int>(EquipItem::SteelSword));
}

void strengthen(VaiwuPet& pet)
{
    pet.rpg().level = 5;
    pet.rpg().alloc = {200, 200, 200, 200, 200};
    normalizeRpg(pet.character(), pet.rpg());
}

void testBattleVictoryAndSingleSettlement()
{
    VaiwuPet pet;
    pet.startNew(CharacterId::Aoi, "", 1000);
    strengthen(pet);
    vaiwu::android::BattleSession battle;
    CHECK(battle.start(pet, false, 11) == ActionResult::Ok);
    const auto coinsBefore = pet.coins();
    for (int turn = 0; turn < 20 && !battle.state().over; ++turn) {
        battle.takeTurn(pet, RpgAction::Attack, 100 + turn);
    }
    CHECK(battle.state().over);
    CHECK(battle.state().outcome == vaiwu::android::BattleOutcome::Victory);
    CHECK(pet.coins() > coinsBefore);
    CHECK(pet.rpg().xp > 0 || pet.rpg().level > 5);
    const auto coinsAfter = pet.coins();
    const auto xpAfter = pet.rpg().xp;
    battle.takeTurn(pet, RpgAction::Attack, 999);
    CHECK(pet.coins() == coinsAfter);
    CHECK(pet.rpg().xp == xpAfter);
    battle.leave();
    CHECK(!battle.state().active);
}

void testBattleDefeatIsNotPetDeath()
{
    VaiwuPet pet;
    pet.startNew(CharacterId::Rin, "", 1000);
    pet.addCoins(100);
    pet.rpg().hp = 1;
    vaiwu::android::BattleSession battle;
    CHECK(battle.start(pet, false, 12) == ActionResult::Ok);
    for (int turn = 0; turn < 30 && !battle.state().over; ++turn) {
        battle.takeTurn(pet, RpgAction::Attack, 200 + turn);
    }
    CHECK(battle.state().outcome == vaiwu::android::BattleOutcome::Defeat);
    CHECK(pet.life() != LifeState::Dead);
    CHECK(pet.rpg().hp > 0);
    CHECK(pet.coins() < 100);
    CHECK(!battle.state().dungeon.active);
}

void testBattleEscapeKeepsPetAlive()
{
    VaiwuPet pet;
    pet.startNew(CharacterId::Hina, "", 1000);
    vaiwu::android::BattleSession battle;
    CHECK(battle.start(pet, false, 13) == ActionResult::Ok);
    for (int turn = 0; turn < 20 && !battle.state().over; ++turn) {
        battle.takeTurn(pet, RpgAction::Run, 300 + turn);
    }
    CHECK(battle.state().over);
    CHECK(battle.state().outcome == vaiwu::android::BattleOutcome::Escaped ||
        battle.state().outcome == vaiwu::android::BattleOutcome::Defeat);
    CHECK(pet.life() != LifeState::Dead);
}

void testDungeonRestBossChestAndEarlyExit()
{
    VaiwuPet pet;
    pet.startNew(CharacterId::Akane, "", 1000);
    strengthen(pet);
    vaiwu::android::BattleSession battle;
    CHECK(battle.start(pet, true, 14) == ActionResult::Ok);
    CHECK(battle.state().dungeon.rooms >= kDungeonMinRooms);
    CHECK(battle.state().dungeon.rooms <= kDungeonMaxRooms);
    const int totalRooms = battle.state().dungeon.rooms;
    CHECK(!battle.nextRoom(pet, 1));
    int clearedRooms = 0;
    for (int guard = 0; guard < 200 && battle.state().active && !battle.state().cleared; ++guard) {
        if (battle.state().awaitingRest) {
            const int oldRoom = battle.state().dungeon.room;
            CHECK(battle.nextRoom(pet, 500 + guard));
            CHECK(battle.state().dungeon.room == oldRoom + 1);
            CHECK(!battle.state().over);
            continue;
        }
        battle.takeTurn(pet, RpgAction::Attack, 600 + guard);
        if (battle.state().over && battle.state().outcome == vaiwu::android::BattleOutcome::Victory) {
            ++clearedRooms;
        }
        if (battle.state().outcome == vaiwu::android::BattleOutcome::Defeat) {
            CHECK(false);
            break;
        }
    }
    CHECK(battle.state().cleared);
    CHECK(battle.state().chestGold > 0);
    CHECK(clearedRooms == totalRooms);
    CHECK(!battle.nextRoom(pet, 1));

    vaiwu::android::BattleSession earlyExit;
    CHECK(earlyExit.start(pet, true, 15) == ActionResult::Ok);
    const auto banked = pet.coins();
    earlyExit.leave();
    CHECK(!earlyExit.state().active);
    CHECK(pet.coins() == banked);
}

void testStatAllocationValidation()
{
    VaiwuPet pet;
    pet.startNew(CharacterId::Yuki, "", 1000);
    vaiwu::android::BattleSession battle;
    battle.allocate(pet, 0);
    CHECK(pet.rpg().alloc.str == 0);
    pet.rpg().statPoints = 2;
    battle.allocate(pet, 2);
    battle.allocate(pet, 4);
    battle.allocate(pet, 3);
    battle.allocate(pet, -1);
    battle.allocate(pet, 5);
    CHECK(pet.rpg().statPoints == 0);
    CHECK(pet.rpg().alloc.agi == 1);
    CHECK(pet.rpg().alloc.luck == 1);
    CHECK(pet.rpg().alloc.mag == 0);
}

void testAssistantAndOfflinePhrasesRemainAvailable()
{
    const auto reminder = parseIntent("напомни через 2 часа позвонить", Lang::Ru);
    CHECK(reminder.kind == IntentKind::SetReminderIn);
    CHECK(reminder.afterMinutes == 120);
    const auto alarm = parseIntent("разбуди в 7:30", Lang::Ru);
    CHECK(alarm.kind == IntentKind::SetAlarm);
    CHECK(alarm.hour == 7);
    CHECK(alarm.minute == 30);
    const auto conversion = parseIntent("5 km in miles", Lang::Ru);
    CHECK(conversion.kind == IntentKind::Convert);
    CHECK(std::strstr(conversion.convertResult, "3.11") != nullptr);
    for (int context = 0; context <= static_cast<int>(PhraseContext::Fainted); ++context) {
        const char* phrase = offlinePhrase(CharacterId::Aoi,
            static_cast<PhraseContext>(context), 42, Lang::Ru);
        CHECK(phrase != nullptr && phrase[0] != '\0');
    }
}

void testAcceptedEquipmentReturnsTrue()
{
    RpgProgress progress;
    normalizeRpg(CharacterId::Rin, progress);
    grantEquip(progress, EquipItem::SteelSword);
    grantEquip(progress, EquipItem::MagicStaff);

    CHECK(applyEquipment(CharacterId::Rin, progress, 0,
        static_cast<int>(EquipItem::SteelSword)));
    CHECK(progress.equipped[0] == static_cast<int>(EquipItem::SteelSword));
    CHECK(applyEquipment(CharacterId::Rin, progress, 0,
        static_cast<int>(EquipItem::MagicStaff)));
    CHECK(progress.equipped[0] == static_cast<int>(EquipItem::MagicStaff));
    CHECK(applyEquipment(CharacterId::Rin, progress, 0, -1));
    CHECK(progress.equipped[0] == -1);
}

// A hungry, exhausted pet falls asleep and becomes sick. This is the exact
// state the "medicine is unreachable" report starts from.
void makeSickAndSleepy(VaiwuPet& pet)
{
    pet.startNew(CharacterId::Aoi, "", 1000);
    pet.applyGift(-95.0f, 0.0f, -100.0f, 0.0f); // hunger 5, energy 0
    pet.tick(2000);                              // one tick -> forced sleep
    CHECK(pet.life() == LifeState::Sleeping);
    CHECK(pet.isSick());
}

void testSickSleepingPetCanBeCured()
{
    VaiwuPet pet;
    makeSickAndSleepy(pet);

    // cure() must stay usable while asleep: the app exposes medicine as a
    // care action, and refusing it here would bring the death spiral back.
    pet.cure();
    CHECK(!pet.isSick());
    CHECK(pet.life() != LifeState::Dead);
}

void testWakeKeepsPetAwakeLongEnoughToCare()
{
    VaiwuPet pet;
    makeSickAndSleepy(pet);

    CHECK(pet.wake());
    CHECK(pet.life() == LifeState::Awake);

    // The next tick used to force her straight back to sleep because her
    // energy was still below the threshold.
    pet.tick(3000);
    CHECK(pet.life() == LifeState::Awake);
    CHECK(pet.stats().energy <= 15.0f);

    // Both rescue actions are reachable inside the grace window.
    CHECK(pet.feed() == ActionResult::Ok);
    CHECK(!pet.isSick());
}

void testWakeGraceExpiresAndPetSleepsAgain()
{
    VaiwuPet pet;
    makeSickAndSleepy(pet);
    CHECK(pet.wake());

    // After the grace window she dozes off again on her own.
    pet.tick(2000 + 3 * 60 * 1000);
    CHECK(pet.life() == LifeState::Sleeping);
}

void testSickSleepingPetCanBeRescuedBeforeDeath()
{
    VaiwuPet pet;
    makeSickAndSleepy(pet);

    // Three minutes of sleep at 0.25 hunger/min leaves her alive.
    pet.tick(2000 + 3 * 60 * 1000);
    CHECK(pet.life() != LifeState::Dead);

    pet.cure();
    CHECK(!pet.isSick());
    CHECK(pet.life() != LifeState::Dead);
}

void testPlayRefusesWhenItWouldPutPetToSleep()
{
    VaiwuPet pet;
    pet.startNew(CharacterId::Aoi, "", 1000);
    pet.applyGift(0.0f, 0.0f, -78.0f, 0.0f); // energy 22

    const float before = pet.stats().energy;
    CHECK(pet.startPlay() == ActionResult::TooTired);
    CHECK(pet.stats().energy == before); // a refused round costs nothing
    CHECK(pet.life() == LifeState::Awake);
}

void testPlayStartsWhenEnoughEnergyRemains()
{
    VaiwuPet pet;
    pet.startNew(CharacterId::Aoi, "", 1000);
    pet.applyGift(0.0f, 0.0f, -76.0f, 0.0f); // energy 24

    CHECK(pet.startPlay() == ActionResult::Ok);
    CHECK(pet.life() == LifeState::Awake);
    CHECK(pet.stats().energy == 16.0f);
}

} // namespace

int main()
{
    testRejectedEquipmentReturnsFalse();
    testAcceptedEquipmentReturnsTrue();
    testBattleVictoryAndSingleSettlement();
    testBattleDefeatIsNotPetDeath();
    testBattleEscapeKeepsPetAlive();
    testDungeonRestBossChestAndEarlyExit();
    testStatAllocationValidation();
    testAssistantAndOfflinePhrasesRemainAvailable();
    testSickSleepingPetCanBeCured();
    testWakeKeepsPetAwakeLongEnoughToCare();
    testWakeGraceExpiresAndPetSleepsAgain();
    testSickSleepingPetCanBeRescuedBeforeDeath();
    testPlayRefusesWhenItWouldPutPetToSleep();
    testPlayStartsWhenEnoughEnergyRemains();

    if (failures == 0) {
        std::printf("ALL ANDROID NATIVE TESTS PASSED\n");
        return 0;
    }
    std::printf("%d FAILURES\n", failures);
    return 1;
}
