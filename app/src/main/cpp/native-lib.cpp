#include <jni.h>

#include "AndroidGame.hpp"
#include "JniHelpers.hpp"
#include "NativeState.hpp"

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <random>
#include <string>

#include "vaiwu/game/Characters.hpp"
#include "vaiwu/game/Rpg.hpp"
#include "vaiwu/game/Shop.hpp"
#include "vaiwu/game/VaiwuPet.hpp"

namespace {

// Action ids mirrored by `PetAction` in `GameConstants.kt`.
constexpr jint kActionFeed = 0;
constexpr jint kActionWash = 2;
constexpr jint kActionClean = 3;
constexpr jint kActionTalk = 4;
constexpr jint kActionWake = 5;
constexpr jint kActionMedicine = 6;
constexpr jint kActionHug = 7;
constexpr jint kActionSleep = 8;

// Happiness & reward tuning shared with Android mini-games and care actions.
constexpr float kHugHappinessGain = 6.0f;
constexpr float kDrawHappinessGain = 3.0f;
constexpr std::uint32_t kMaxMiniGameReward = 10U;

// Rock-Paper-Scissors rules (3 choices: rock/paper/scissors, +2 coins on win).
constexpr int kRpsChoiceCount = 3;
constexpr int kRpsWinReward = 2;

// Shop item kind ids mirrored by `ShopItemKind` in `GameConstants.kt`.
constexpr int kShopKindConsumable = 0;
constexpr int kShopKindEquipment = 1;
constexpr int kShopKindPotion = 2;
constexpr int kShopKindMpPotion = 3;

// Free-rice result codes mirrored by `UseResult.FreeRice*` in `GameConstants.kt`.
constexpr jint kFreeRiceOk = 0;
constexpr jint kFreeRiceRefusedDead = 10;
constexpr jint kFreeRiceRefusedSleeping = 11;
constexpr jint kFreeRiceRefusedCaprice = 12;
constexpr jint kFreeRiceNotHungry = 13;
constexpr jint kFreeRiceUnknown = 19;

// Safely unwraps an opaque JNI handle back into its NativePetState pointer.
NativePetState* state(jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    return reinterpret_cast<NativePetState*>(static_cast<intptr_t>(handle));
}

vaiwu::game::VaiwuPet* pet(jlong handle)
{
    auto* s = state(handle);
    return s == nullptr ? nullptr : &s->pet;
}

jstring javaString(JNIEnv* env, const std::string& value)
{
    return vaiwu::android::toJavaString(env, value);
}

void appendBonus(std::string& text, vaiwu::game::Str label, int value)
{
    if (value == 0) {
        return;
    }
    if (!text.empty()) {
        text += " · ";
    }
    text += vaiwu::game::tr(vaiwu::game::Lang::Ru, label);
    text += value > 0 ? " +" : " ";
    text += std::to_string(value);
}

std::string equipBonuses(vaiwu::game::EquipItem item)
{
    using namespace vaiwu::game;
    const RpgStats& bonus = equipInfo(item).bonus;
    std::string text;
    appendBonus(text, Str::StatShortStr, bonus.str);
    appendBonus(text, Str::StatShortVit, bonus.vit);
    appendBonus(text, Str::StatShortAgi, bonus.agi);
    appendBonus(text, Str::StatShortMag, bonus.mag);
    appendBonus(text, Str::StatShortLuck, bonus.luck);
    return text;
}

std::string shopEffect(const vaiwu::game::ShopItemInfo& info)
{
    using namespace vaiwu::game;
    if (info.equip >= 0) {
        return equipBonuses(static_cast<EquipItem>(info.equip));
    }
    if (info.potion || info.mpPotion) {
        char text[48];
        std::snprintf(text, sizeof(text), tr(Lang::Ru,
            info.potion ? Str::RpgPotionFmt : Str::RpgMpPotionFmt), 1);
        return text;
    }
    std::string text;
    appendBonus(text, Str::BarFood, static_cast<int>(info.hunger));
    appendBonus(text, Str::BarMood, static_cast<int>(info.happiness));
    appendBonus(text, Str::BarEnergy, static_cast<int>(info.energy));
    return text;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_su_neit_vaiwu_NativePet_nativeCreate(JNIEnv*, jobject)
{
    return static_cast<jlong>(reinterpret_cast<intptr_t>(new NativePetState()));
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeDestroy(JNIEnv*, jobject, jlong handle)
{
    delete state(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeStartNew(
    JNIEnv* env, jobject, jlong handle, jint characterId, jstring name, jlong nowMs)
{
    auto* s = state(handle);
    if (s == nullptr || characterId < 0 || characterId >= vaiwu::game::kCharacterCount) {
        return;
    }
    std::string petName;
    if (name != nullptr) {
        (void)vaiwu::android::fromJavaString(env, name, petName);
    }
    s->rpsStarted = false;
    s->battle.leave();
    s->pet.startNew(static_cast<vaiwu::game::CharacterId>(characterId), petName,
        static_cast<std::uint64_t>(nowMs));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeLoad(
    JNIEnv* env, jobject, jlong handle, jstring data, jlong nowMs)
{
    auto* s = state(handle);
    if (s == nullptr) {
        return JNI_FALSE;
    }
    std::string utfData;
    if (!vaiwu::android::fromJavaString(env, data, utfData)) {
        return JNI_FALSE;
    }
    const bool loaded = s->pet.deserialize(utfData, static_cast<std::uint64_t>(nowMs));
    s->rpsStarted = false;
    s->battle.leave();
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_su_neit_vaiwu_NativePet_nativeSave(JNIEnv* env, jobject, jlong handle)
{
    auto* p = pet(handle);
    if (p == nullptr) {
        return javaString(env, "");
    }
    return javaString(env, p->serialize());
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeTick(JNIEnv*, jobject, jlong handle, jlong nowMs)
{
    if (auto* p = pet(handle)) {
        p->tick(static_cast<std::uint64_t>(nowMs));
    }
}

// Dispatches a daily care action id from Kotlin (`PetAction`) to VaiwuPet.
extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeAction(JNIEnv*, jobject, jlong handle, jint action)
{
    auto* p = pet(handle);
    if (p == nullptr) {
        return -1;
    }
    switch (action) {
    case kActionFeed:
        return static_cast<jint>(p->feed());
    case kActionWash:
        return static_cast<jint>(p->wash());
    case kActionClean:
        return static_cast<jint>(p->toilet());
    case kActionTalk:
        return static_cast<jint>(p->talk());
    case kActionWake:
        return static_cast<jint>(p->wake() ? vaiwu::game::ActionResult::Ok
                                           : vaiwu::game::ActionResult::RefusedSleeping);
    case kActionMedicine:
        p->cure();
        return 0;
    case kActionHug:
        p->giveAffection(kHugHappinessGain);
        return 0;
    case kActionSleep:
        return static_cast<jint>(p->sleep() ? vaiwu::game::ActionResult::Ok
                                            : vaiwu::game::ActionResult::NotHungry);
    default:
        return -1;
    }
}

// Settles a finished Android mini-game round and caps coin rewards at kMaxMiniGameReward.
extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeFinishGame(
    JNIEnv*, jobject, jlong handle, jint outcome, jint reward)
{
    auto* s = state(handle);
    if (s == nullptr || !s->rpsStarted || outcome < -1 || outcome > 1) {
        return JNI_FALSE;
    }
    s->rpsStarted = false;
    auto& p = s->pet;
    if (outcome > 0) {
        p.applyPlayResult(true);
        const auto safeReward = reward > 0
            ? std::min<std::uint32_t>(static_cast<std::uint32_t>(reward), kMaxMiniGameReward)
            : 0U;
        p.addCoins(safeReward);
    } else if (outcome < 0) {
        p.applyPlayResult(false);
    } else {
        p.giveAffection(kDrawHappinessGain);
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeSnapshot(JNIEnv* env, jobject, jlong handle)
{
    using vaiwu::game::traitsFor;
    const auto* p = pet(handle);
    if (p == nullptr) {
        return nullptr;
    }
    const auto& stats = p->stats();
    const auto& traits = traitsFor(p->character());

    const jclass snapshotClass = env->FindClass("su/neit/vaiwu/PetSnapshot");
    if (snapshotClass == nullptr) {
        return nullptr;
    }
    const jmethodID constructor = env->GetMethodID(snapshotClass, "<init>",
        "(ILjava/lang/String;Ljava/lang/String;FFFFFIIIIZ)V");
    if (constructor == nullptr) {
        env->DeleteLocalRef(snapshotClass);
        return nullptr;
    }

    jstring characterName = javaString(env, traits.name);
    jstring petName = javaString(env, p->name());
    jobject snapshot = nullptr;
    if (characterName != nullptr && petName != nullptr) {
        snapshot = env->NewObject(snapshotClass, constructor,
            static_cast<jint>(p->character()),
            characterName,
            petName,
            static_cast<jfloat>(stats.hunger),
            static_cast<jfloat>(stats.happiness),
            static_cast<jfloat>(stats.energy),
            static_cast<jfloat>(stats.discipline),
            static_cast<jfloat>(stats.weightKg),
            static_cast<jint>(stats.ageSeconds),
            static_cast<jint>(stats.coins),
            static_cast<jint>(p->emotion()),
            static_cast<jint>(p->life()),
            p->isSick() ? JNI_TRUE : JNI_FALSE);
    }
    if (characterName != nullptr) env->DeleteLocalRef(characterName);
    if (petName != nullptr) env->DeleteLocalRef(petName);
    env->DeleteLocalRef(snapshotClass);
    return snapshot;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_su_neit_vaiwu_NativePet_nativeShop(JNIEnv* env, jobject, jlong handle)
{
    using namespace vaiwu::game;
    const auto* p = pet(handle);
    if (p == nullptr) {
        return nullptr;
    }
    const jclass itemClass = env->FindClass("su/neit/vaiwu/ShopItemSnapshot");
    if (itemClass == nullptr) {
        return nullptr;
    }
    const jmethodID constructor = env->GetMethodID(itemClass, "<init>",
        "(IILjava/lang/String;IZLjava/lang/String;IIZ)V");
    if (constructor == nullptr) {
        env->DeleteLocalRef(itemClass);
        return nullptr;
    }

    // Categories are not required to add up to kShopItemCount, so size the
    // array from the actual contents.
    int totalItems = 0;
    for (int category = 0; category < kShopCategoryCount; ++category) {
        int count = 0;
        (void)shopCategoryItems(static_cast<ShopCategory>(category), count);
        totalItems += count;
    }
    jobjectArray result = env->NewObjectArray(totalItems, itemClass, nullptr);
    if (result == nullptr) {
        env->DeleteLocalRef(itemClass);
        return nullptr;
    }
    int outputIndex = 0;
    for (int category = 0; category < kShopCategoryCount; ++category) {
        int count = 0;
        const ShopItem* items = shopCategoryItems(static_cast<ShopCategory>(category), count);
        for (int index = 0; index < count && outputIndex < totalItems; ++index) {
            const int id = static_cast<int>(items[index]);
            const ShopItemInfo& info = shopItem(items[index]);
            const bool owned = info.equip >= 0
                && ownsEquip(p->rpg(), static_cast<EquipItem>(info.equip));
            const int kind = info.equip >= 0 ? kShopKindEquipment
                : info.potion ? kShopKindPotion
                : info.mpPotion ? kShopKindMpPotion
                : kShopKindConsumable;
            const int carried = kind == kShopKindConsumable ? p->consumableCount(id) : 0;
            const bool usable = kind == kShopKindConsumable && canUseShopItem(*p, items[index]);
            jstring nameStr = javaString(env, tr(Lang::Ru, info.nameId));
            jstring effectStr = javaString(env, shopEffect(info));
            jobject item = (nameStr != nullptr && effectStr != nullptr)
                ? env->NewObject(itemClass, constructor,
                    static_cast<jint>(id), static_cast<jint>(category),
                    nameStr,
                    static_cast<jint>(info.price), owned ? JNI_TRUE : JNI_FALSE,
                    effectStr, static_cast<jint>(kind),
                    static_cast<jint>(carried), usable ? JNI_TRUE : JNI_FALSE)
                : nullptr;
            if (nameStr != nullptr) env->DeleteLocalRef(nameStr);
            if (effectStr != nullptr) env->DeleteLocalRef(effectStr);
            if (item == nullptr) {
                env->DeleteLocalRef(itemClass);
                env->DeleteLocalRef(result);
                return nullptr;
            }
            env->SetObjectArrayElement(result, outputIndex++, item);
            env->DeleteLocalRef(item);
        }
    }
    env->DeleteLocalRef(itemClass);
    if (outputIndex != totalItems) {
        env->DeleteLocalRef(result);
        return nullptr;
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeBuy(JNIEnv*, jobject, jlong handle, jint itemId)
{
    using namespace vaiwu::game;
    auto* p = pet(handle);
    if (p == nullptr || itemId < 0 || itemId >= kShopItemCount) {
        return JNI_FALSE;
    }
    // App purchases go into the bag; the ESP firmware keeps the instant
    // buyShopItem() behaviour.
    return buyShopItemToBag(*p, static_cast<ShopItem>(itemId)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeUseItem(JNIEnv*, jobject, jlong handle, jint itemId)
{
    using namespace vaiwu::game;
    auto* p = pet(handle);
    if (p == nullptr || itemId < 0 || itemId >= kShopItemCount) {
        return static_cast<jint>(UseResult::NotOwned);
    }
    return static_cast<jint>(useShopItem(*p, static_cast<ShopItem>(itemId)));
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeFeedWith(JNIEnv*, jobject, jlong handle, jint itemId)
{
    using namespace vaiwu::game;
    VaiwuPet* p = pet(handle);
    if (p == nullptr) {
        return static_cast<jint>(UseResult::NotApplicable);
    }
    if (itemId < 0) {
        // Free rice keeps the classic feed rules (caprice, "already full").
        switch (p->feed()) {
        case ActionResult::Ok: return kFreeRiceOk;
        case ActionResult::RefusedDead: return kFreeRiceRefusedDead;
        case ActionResult::RefusedSleeping: return kFreeRiceRefusedSleeping;
        case ActionResult::RefusedCaprice: return kFreeRiceRefusedCaprice;
        case ActionResult::NotHungry: return kFreeRiceNotHungry;
        default: return kFreeRiceUnknown;
        }
    }
    if (itemId >= kShopItemCount) {
        return static_cast<jint>(UseResult::NotApplicable);
    }
    const auto item = static_cast<ShopItem>(itemId);
    // A dish already in the bag is used for free; otherwise the menu acts as
    // a shortcut that buys and feeds in one step (and refuses to spend when
    // the effect would be wasted).
    return static_cast<jint>(p->consumableCount(itemId) > 0
        ? useShopItem(*p, item)
        : buyAndUseShopItem(*p, item));
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeInventory(JNIEnv* env, jobject, jlong handle)
{
    using namespace vaiwu::game;
    auto* p = pet(handle);
    if (p == nullptr) {
        return nullptr;
    }
    RpgProgress& progress = p->rpg();
    normalizeRpg(p->character(), progress);
    const RpgStats total = totalStats(p->character(), progress);

    const jclass equipmentClass = env->FindClass("su/neit/vaiwu/EquipmentSnapshot");
    if (equipmentClass == nullptr) {
        return nullptr;
    }
    const jmethodID equipmentConstructor = env->GetMethodID(equipmentClass, "<init>",
        "(IILjava/lang/String;Ljava/lang/String;ZZI)V");
    if (equipmentConstructor == nullptr) {
        env->DeleteLocalRef(equipmentClass);
        return nullptr;
    }
    jobjectArray equipment = env->NewObjectArray(kEquipItemCount, equipmentClass, nullptr);
    if (equipment == nullptr) {
        env->DeleteLocalRef(equipmentClass);
        return nullptr;
    }
    for (int id = 0; id < kEquipItemCount; ++id) {
        const auto item = static_cast<EquipItem>(id);
        const EquipInfo& info = equipInfo(item);
        const int slot = static_cast<int>(info.slot);
        int shopPrice = 0;
        for (int shopId = 0; shopId < kShopItemCount; ++shopId) {
            const ShopItemInfo& shopInfo = shopItem(static_cast<ShopItem>(shopId));
            if (shopInfo.equip == id) {
                shopPrice = static_cast<int>(shopInfo.price);
                break;
            }
        }
        jstring nameStr = javaString(env, tr(Lang::Ru, info.nameId));
        jstring bonusStr = javaString(env, equipBonuses(item));
        jobject entry = (nameStr != nullptr && bonusStr != nullptr)
            ? env->NewObject(equipmentClass, equipmentConstructor,
                static_cast<jint>(id), static_cast<jint>(slot),
                nameStr, bonusStr,
                ownsEquip(progress, item) ? JNI_TRUE : JNI_FALSE,
                progress.equipped[slot] == id ? JNI_TRUE : JNI_FALSE,
                static_cast<jint>(equipSellPrice(shopPrice)))
            : nullptr;
        if (nameStr != nullptr) env->DeleteLocalRef(nameStr);
        if (bonusStr != nullptr) env->DeleteLocalRef(bonusStr);
        if (entry == nullptr) {
            env->DeleteLocalRef(equipmentClass);
            env->DeleteLocalRef(equipment);
            return nullptr;
        }
        env->SetObjectArrayElement(equipment, id, entry);
        env->DeleteLocalRef(entry);
    }
    env->DeleteLocalRef(equipmentClass);

    const jclass inventoryClass = env->FindClass("su/neit/vaiwu/InventorySnapshot");
    if (inventoryClass == nullptr) {
        env->DeleteLocalRef(equipment);
        return nullptr;
    }
    const jmethodID constructor = env->GetMethodID(inventoryClass, "<init>",
        "(IIIIIIIIIIIIIII[Lsu/neit/vaiwu/EquipmentSnapshot;)V");
    if (constructor == nullptr) {
        env->DeleteLocalRef(inventoryClass);
        env->DeleteLocalRef(equipment);
        return nullptr;
    }
    jobject inventory = env->NewObject(inventoryClass, constructor,
        static_cast<jint>(progress.level), static_cast<jint>(progress.xp),
        static_cast<jint>(xpForNext(progress.level)),
        static_cast<jint>(progress.hp), static_cast<jint>(hpMaxFor(p->character(), progress)),
        static_cast<jint>(progress.mp), static_cast<jint>(mpMaxFor(p->character(), progress)),
        static_cast<jint>(progress.potions), static_cast<jint>(progress.mpPotions),
        static_cast<jint>(total.str), static_cast<jint>(total.vit),
        static_cast<jint>(total.agi), static_cast<jint>(total.mag), static_cast<jint>(total.luck),
        static_cast<jint>(progress.statPoints), equipment);
    env->DeleteLocalRef(inventoryClass);
    env->DeleteLocalRef(equipment);
    return inventory;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeEquip(JNIEnv*, jobject, jlong handle, jint slot, jint itemId)
{
    auto* p = pet(handle);
    if (p == nullptr) {
        return JNI_FALSE;
    }
    return vaiwu::android::applyEquipment(p->character(), p->rpg(), slot, itemId)
        ? JNI_TRUE
        : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeSell(JNIEnv*, jobject, jlong handle, jint itemId)
{
    using namespace vaiwu::game;
    auto* p = pet(handle);
    if (p == nullptr || itemId < 0 || itemId >= kEquipItemCount) {
        return 0;
    }
    return static_cast<jint>(sellEquip(*p, static_cast<EquipItem>(itemId)));
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeStartPlay(JNIEnv*, jobject, jlong handle)
{
    auto* s = state(handle);
    if (s == nullptr) {
        return -1;
    }
    const auto result = s->pet.startPlay();
    s->rpsStarted = result == vaiwu::game::ActionResult::Ok;
    return static_cast<jint>(result);
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeCancelPlay(JNIEnv*, jobject, jlong handle)
{
    if (auto* s = state(handle)) {
        s->rpsStarted = false;
    }
}

// Resolves a single Rock-Paper-Scissors round and returns the RpsResult snapshot.
extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativePlayRps(JNIEnv* env, jobject, jlong handle, jint userChoice)
{
    auto* s = state(handle);
    if (s == nullptr || !s->rpsStarted || s->pet.life() != vaiwu::game::LifeState::Awake ||
        userChoice < 0 || userChoice >= kRpsChoiceCount) {
        return nullptr;
    }
    s->rpsStarted = false;
    const int petChoice = std::uniform_int_distribution<int>(0, kRpsChoiceCount - 1)(s->rng);
    const int difference = (userChoice - petChoice + kRpsChoiceCount) % kRpsChoiceCount;
    const int outcome = difference == 0 ? 0 : difference == 1 ? 1 : -1;
    const int reward = outcome > 0 ? kRpsWinReward : 0;
    if (outcome > 0) {
        s->pet.applyPlayResult(true);
        s->pet.addCoins(reward);
    } else if (outcome < 0) {
        s->pet.applyPlayResult(false);
    } else {
        s->pet.giveAffection(kDrawHappinessGain);
    }
    const jclass resultClass = env->FindClass("su/neit/vaiwu/RpsResult");
    if (resultClass == nullptr) {
        return nullptr;
    }
    const jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(III)V");
    if (constructor == nullptr) {
        env->DeleteLocalRef(resultClass);
        return nullptr;
    }
    jobject result = env->NewObject(resultClass, constructor,
        static_cast<jint>(petChoice), static_cast<jint>(outcome), static_cast<jint>(reward));
    env->DeleteLocalRef(resultClass);
    return result;
}
