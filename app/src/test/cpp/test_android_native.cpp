#include "AndroidGame.hpp"
#include "Battle.hpp"
#include "Utf8Codec.hpp"
#include "vaiwu/game/Assistant.hpp"
#include "vaiwu/game/OfflinePhrases.hpp"
#include "vaiwu/game/SoundSynth.hpp"

#include <cmath>
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

void testSoundSynthCoversEveryEffect()
{
    constexpr std::uint32_t kRate = 22050;
    constexpr int kCount = static_cast<int>(Sfx::Encounter) + 1;

    for (int i = 0; i < kCount; ++i) {
        const auto effect = static_cast<Sfx>(i);
        const auto pcm = renderSfx(effect, kRate);
        CHECK(!pcm.empty());
        CHECK(pcm.size() % 2U == 0U); // whole PCM16 frames

        // Every effect is a short UI blip, never a multi-second track.
        const std::size_t durationMs = (pcm.size() / 2U) * 1000U / kRate;
        CHECK(durationMs > 0U && durationMs < 2000U);

        // The synthesizer is deterministic: the JNI bridge relies on it.
        CHECK(pcm == renderSfx(effect, kRate));

        // Buffers are expressed in samples, so half the rate shrinks the PCM.
        const auto halfRate = renderSfx(effect, kRate / 2U);
        CHECK(halfRate.size() < pcm.size());
    }
}

void testUtf8Utf16RoundTripHandlesEmojiAndMalformedInput()
{
    using vaiwu::android::utf16ToUtf8;
    using vaiwu::android::utf8ToUtf16;

    // ASCII + Cyrillic + 3-byte BMP + 4-byte emoji (U+1F431 cat face).
    const std::string original = "Aoi \xD0\x9F\xD1\x80\xD0\xB8\xD0\xB2\xD0\xB5\xD1\x82 \xE2\x9C\xA8 \xF0\x9F\x90\xB1";
    const std::u16string u16 = utf8ToUtf16(original);
    CHECK(!u16.empty());
    const std::string roundTripped = utf16ToUtf8(
        reinterpret_cast<const std::uint16_t*>(u16.data()), u16.size());
    CHECK(roundTripped == original);

    // Lone surrogates in UTF-16 become U+FFFD instead of invalid UTF-8.
    const std::uint16_t loneHigh[] = {0xD83D, 'A'};
    const std::string sanitized16 = utf16ToUtf8(loneHigh, 2);
    CHECK(sanitized16 == "\xEF\xBF\xBD" "A");

    // Truncated 4-byte UTF-8 sequence becomes U+FFFD instead of crashing.
    const std::u16string sanitized8 = utf8ToUtf16(std::string("\xF0\x9F\x90", 3));
    CHECK(!sanitized8.empty());
    CHECK(sanitized8[0] == u'\uFFFD');
}

void testDeserializeRejectsNonFiniteAndClampsCorruptValues()
{
    VaiwuPet pet;
    CHECK(!pet.deserialize("V1 0 nan 80 100 60 15 0 0 0\nAoi", 1000));
    CHECK(!pet.deserialize("V1 0 50 inf 100 60 15 0 0 0\nAoi", 1000));
    CHECK(!pet.deserialize("V1 0 50 80 -inf 60 15 0 0 0\nAoi", 1000));

    // Extreme coins and RPG allocations are clamped safely.
    const std::string extreme =
        "V3 0 80 80 80 60 15 100 0 0 3000000000 "
        "999 -50 99999 -10 999999 0 0 0 999999 999999 999\nAoi";
    CHECK(pet.deserialize(extreme, 1000));
    CHECK(pet.coins() == 99999U);
    CHECK(pet.rpg().level == 99);
    CHECK(pet.rpg().xp == 0);
    CHECK(pet.rpg().statPoints == 300);
    CHECK(pet.rpg().alloc.str == 0);
    CHECK(pet.rpg().alloc.vit == 500);
    CHECK(pet.rpg().potions == 99);
    CHECK(std::isfinite(pet.stats().hunger));
}

void testTacticalSettlementVictoryLevelUpAndClamping()
{
    VaiwuPet pet;
    pet.startNew(CharacterId::Aoi, "Aoi", 1000);
    pet.rpg().potions = 2;
    pet.rpg().mpPotions = 1;
    const auto coinsBefore = pet.coins();

    vaiwu::android::TacticalSettlement win{};
    win.outcome = static_cast<int>(vaiwu::android::BattleOutcome::Victory);
    win.hpLeft = 10;
    win.mpLeft = 3;
    // Attempt to inflate potions beyond inventory must be clamped to owned count.
    win.potionsLeft = 50;
    win.mpPotionsLeft = 0;
    win.xpGained = xpForNext(1) + 5;
    win.goldDelta = 15;
    win.applyPlayOutcome = true;

    const int gained = vaiwu::android::settleTacticalBattle(pet, win);
    CHECK(gained == 1);
    CHECK(pet.rpg().level == 2);
    CHECK(pet.rpg().xp == 5);
    CHECK(pet.rpg().statPoints == kPointsPerLevel);
    CHECK(pet.rpg().hp == hpMaxFor(pet.character(), pet.rpg()));
    CHECK(pet.rpg().mp == mpMaxFor(pet.character(), pet.rpg()));
    CHECK(pet.rpg().potions == 2);
    CHECK(pet.rpg().mpPotions == 0);
    CHECK(pet.coins() == coinsBefore + 15U);

    // Invalid outcome rejected without modifying state.
    vaiwu::android::TacticalSettlement invalid{};
    invalid.outcome = 99;
    CHECK(vaiwu::android::settleTacticalBattle(pet, invalid) == -1);
}

void testTacticalSettlementDefeatEscapeAndRest()
{
    VaiwuPet pet;
    pet.startNew(CharacterId::Rin, "Rin", 1000);
    pet.addCoins(20);
    const int hpMax = hpMaxFor(pet.character(), pet.rpg());

    // Defeat faints back home (~30% HP), never kills pet, clamps coin loss.
    vaiwu::android::TacticalSettlement defeat{};
    defeat.outcome = static_cast<int>(vaiwu::android::BattleOutcome::Defeat);
    defeat.hpLeft = 0;
    defeat.mpLeft = 2;
    defeat.potionsLeft = 0;
    defeat.mpPotionsLeft = 0;
    defeat.xpGained = 50; // ignored on defeat
    defeat.goldDelta = -999;
    defeat.applyPlayOutcome = true;

    CHECK(vaiwu::android::settleTacticalBattle(pet, defeat) == 0);
    CHECK(pet.life() != LifeState::Dead);
    CHECK(pet.rpg().hp == std::max(1, hpMax * 3 / 10));
    CHECK(pet.coins() == 0U);
    CHECK(pet.rpg().xp == 0);

    // Rest between dungeon rooms recovers HP and MP.
    const int hpBeforeRest = pet.rpg().hp;
    const int mpBeforeRest = pet.rpg().mp;
    CHECK(vaiwu::android::restBetweenTacticalRooms(pet));
    CHECK(pet.rpg().hp > hpBeforeRest);
    CHECK(pet.rpg().mp >= mpBeforeRest);

    // Escape keeps surviving HP (>= 1) and banked gold.
    vaiwu::android::TacticalSettlement escape{};
    escape.outcome = static_cast<int>(vaiwu::android::BattleOutcome::Escaped);
    escape.hpLeft = 0;
    escape.mpLeft = 4;
    escape.potionsLeft = 0;
    escape.mpPotionsLeft = 0;
    escape.goldDelta = 7;
    escape.applyPlayOutcome = false;

    CHECK(vaiwu::android::settleTacticalBattle(pet, escape) == 0);
    CHECK(pet.rpg().hp == 1);
    CHECK(pet.coins() == 7U);
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
    testSoundSynthCoversEveryEffect();
    testUtf8Utf16RoundTripHandlesEmojiAndMalformedInput();
    testDeserializeRejectsNonFiniteAndClampsCorruptValues();
    testTacticalSettlementVictoryLevelUpAndClamping();
    testTacticalSettlementDefeatEscapeAndRest();

    if (failures == 0) {
        std::printf("ALL ANDROID NATIVE TESTS PASSED\n");
        return 0;
    }
    std::printf("%d FAILURES\n", failures);
    return 1;
}
