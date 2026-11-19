#include <jni.h>

#include "AndroidGame.hpp"
#include "NativeState.hpp"

#include <cstdint>
#include <cstdio>
#include <random>
#include <string>

#include "vaiwu/game/Characters.hpp"
#include "vaiwu/game/Rpg.hpp"
#include "vaiwu/game/Shop.hpp"
#include "vaiwu/game/VaiwuPet.hpp"

namespace {

NativePetState* state(jlong handle)
{
    return reinterpret_cast<NativePetState*>(static_cast<intptr_t>(handle));
}

vaiwu::game::VaiwuPet* pet(jlong handle)
{
    return &state(handle)->pet;
}

jstring javaString(JNIEnv* env, const std::string& value)
{
    return env->NewStringUTF(value.c_str());
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
    if (characterId < 0 || characterId >= vaiwu::game::kCharacterCount) {
        return;
    }
    const char* utfName = env->GetStringUTFChars(name, nullptr);
    const std::string petName = utfName == nullptr ? "" : utfName;
    if (utfName != nullptr) {
        env->ReleaseStringUTFChars(name, utfName);
    }
    state(handle)->rpsStarted = false;
    state(handle)->battle.leave();
    pet(handle)->startNew(static_cast<vaiwu::game::CharacterId>(characterId), petName,
        static_cast<std::uint64_t>(nowMs));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeLoad(
    JNIEnv* env, jobject, jlong handle, jstring data, jlong nowMs)
{
    const char* utfData = env->GetStringUTFChars(data, nullptr);
    if (utfData == nullptr) {
        return JNI_FALSE;
    }
    const bool loaded = pet(handle)->deserialize(utfData, static_cast<std::uint64_t>(nowMs));
    state(handle)->rpsStarted = false;
    state(handle)->battle.leave();
    env->ReleaseStringUTFChars(data, utfData);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_su_neit_vaiwu_NativePet_nativeSave(JNIEnv* env, jobject, jlong handle)
{
    return javaString(env, pet(handle)->serialize());
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeTick(JNIEnv*, jobject, jlong handle, jlong nowMs)
{
    pet(handle)->tick(static_cast<std::uint64_t>(nowMs));
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeAction(JNIEnv*, jobject, jlong handle, jint action)
{
    auto* p = pet(handle);
    switch (action) {
    case 0:
        return static_cast<jint>(p->feed());
    case 2:
        return static_cast<jint>(p->wash());
    case 3:
        return static_cast<jint>(p->toilet());
    case 4:
        return static_cast<jint>(p->talk());
    case 5:
        return static_cast<jint>(p->wake() ? vaiwu::game::ActionResult::Ok
                                           : vaiwu::game::ActionResult::RefusedSleeping);
    case 6:
        p->cure();
        return 0;
    case 7:
        p->giveAffection(6.0f);
        return 0;
    default:
        return -1;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeFinishGame(
    JNIEnv*, jobject, jlong handle, jint outcome, jint reward)
{
    auto* s = state(handle);
    if (!s->rpsStarted || outcome < -1 || outcome > 1) {
        return JNI_FALSE;
    }
    s->rpsStarted = false;
    auto* p = pet(handle);
    if (outcome > 0) {
        p->applyPlayResult(true);
        p->addCoins(reward > 0 ? static_cast<std::uint32_t>(reward) : 0U);
    } else if (outcome < 0) {
        p->applyPlayResult(false);
    } else {
        p->giveAffection(3.0f);
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeSnapshot(JNIEnv* env, jobject, jlong handle)
{
    using vaiwu::game::traitsFor;
    const auto* p = pet(handle);
    const auto& stats = p->stats();
    const auto& traits = traitsFor(p->character());

    const jclass snapshotClass = env->FindClass("su/neit/vaiwu/PetSnapshot");
    if (snapshotClass == nullptr) {
        return nullptr;
    }
    const jmethodID constructor = env->GetMethodID(snapshotClass, "<init>",
        "(ILjava/lang/String;Ljava/lang/String;FFFFFIIIIZ)V");
    if (constructor == nullptr) {
        return nullptr;
    }

    return env->NewObject(snapshotClass, constructor,
        static_cast<jint>(p->character()),
        javaString(env, traits.name),
        javaString(env, p->name()),
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

extern "C" JNIEXPORT jobjectArray JNICALL
Java_su_neit_vaiwu_NativePet_nativeShop(JNIEnv* env, jobject, jlong handle)
{
    using namespace vaiwu::game;
    const auto* p = pet(handle);
    const jclass itemClass = env->FindClass("su/neit/vaiwu/ShopItemSnapshot");
    if (itemClass == nullptr) {
        return nullptr;
    }
    const jmethodID constructor = env->GetMethodID(itemClass, "<init>",
        "(IILjava/lang/String;IZLjava/lang/String;IIZ)V");
    if (constructor == nullptr) {
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
            const int kind = info.equip >= 0 ? 1 : info.potion ? 2 : info.mpPotion ? 3 : 0;
            const int carried = kind == 0 ? p->consumableCount(id) : 0;
            const bool usable = kind == 0 && canUseShopItem(*p, items[index]);
            jobject item = env->NewObject(itemClass, constructor,
                static_cast<jint>(id), static_cast<jint>(category),
                javaString(env, tr(Lang::Ru, info.nameId)),
                static_cast<jint>(info.price), owned ? JNI_TRUE : JNI_FALSE,
                javaString(env, shopEffect(info)), static_cast<jint>(kind),
                static_cast<jint>(carried), usable ? JNI_TRUE : JNI_FALSE);
            if (item == nullptr) {
                return nullptr;
            }
            env->SetObjectArrayElement(result, outputIndex++, item);
            env->DeleteLocalRef(item);
        }
    }
    if (outputIndex != totalItems) {
        return nullptr;
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeBuy(JNIEnv*, jobject, jlong handle, jint itemId)
{
    using namespace vaiwu::game;
    if (itemId < 0 || itemId >= kShopItemCount) {
        return JNI_FALSE;
    }
    // App purchases go into the bag; the ESP firmware keeps the instant
    // buyShopItem() behaviour.
    return buyShopItemToBag(*pet(handle), static_cast<ShopItem>(itemId)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeUseItem(JNIEnv*, jobject, jlong handle, jint itemId)
{
    using namespace vaiwu::game;
    if (itemId < 0 || itemId >= kShopItemCount) {
        return static_cast<jint>(UseResult::NotOwned);
    }
    return static_cast<jint>(useShopItem(*pet(handle), static_cast<ShopItem>(itemId)));
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeFeedWith(JNIEnv*, jobject, jlong handle, jint itemId)
{
    using namespace vaiwu::game;
    VaiwuPet* p = pet(handle);
    if (itemId < 0) {
        // Free rice keeps the classic feed rules (caprice, "already full").
        switch (p->feed()) {
        case ActionResult::Ok: return 0;
        case ActionResult::RefusedDead: return 10;
        case ActionResult::RefusedSleeping: return 11;
        case ActionResult::RefusedCaprice: return 12;
        case ActionResult::NotHungry: return 13;
        default: return 19;
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
        return nullptr;
    }
    jobjectArray equipment = env->NewObjectArray(kEquipItemCount, equipmentClass, nullptr);
    if (equipment == nullptr) {
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
        jobject entry = env->NewObject(equipmentClass, equipmentConstructor,
            static_cast<jint>(id), static_cast<jint>(slot),
            javaString(env, tr(Lang::Ru, info.nameId)), javaString(env, equipBonuses(item)),
            ownsEquip(progress, item) ? JNI_TRUE : JNI_FALSE,
            progress.equipped[slot] == id ? JNI_TRUE : JNI_FALSE,
            static_cast<jint>(equipSellPrice(shopPrice)));
        env->SetObjectArrayElement(equipment, id, entry);
        env->DeleteLocalRef(entry);
    }

    const jclass inventoryClass = env->FindClass("su/neit/vaiwu/InventorySnapshot");
    if (inventoryClass == nullptr) {
        return nullptr;
    }
    const jmethodID constructor = env->GetMethodID(inventoryClass, "<init>",
        "(IIIIIIIIIIIIIII[Lsu/neit/vaiwu/EquipmentSnapshot;)V");
    if (constructor == nullptr) {
        return nullptr;
    }
    return env->NewObject(inventoryClass, constructor,
        static_cast<jint>(progress.level), static_cast<jint>(progress.xp),
        static_cast<jint>(xpForNext(progress.level)),
        static_cast<jint>(progress.hp), static_cast<jint>(hpMaxFor(p->character(), progress)),
        static_cast<jint>(progress.mp), static_cast<jint>(mpMaxFor(p->character(), progress)),
        static_cast<jint>(progress.potions), static_cast<jint>(progress.mpPotions),
        static_cast<jint>(total.str), static_cast<jint>(total.vit),
        static_cast<jint>(total.agi), static_cast<jint>(total.mag), static_cast<jint>(total.luck),
        static_cast<jint>(progress.statPoints), equipment);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeEquip(JNIEnv*, jobject, jlong handle, jint slot, jint itemId)
{
    auto* p = pet(handle);
    return vaiwu::android::applyEquipment(p->character(), p->rpg(), slot, itemId)
        ? JNI_TRUE
        : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeSell(JNIEnv*, jobject, jlong handle, jint itemId)
{
    using namespace vaiwu::game;
    if (itemId < 0 || itemId >= kEquipItemCount) {
        return 0;
    }
    return static_cast<jint>(sellEquip(*pet(handle), static_cast<EquipItem>(itemId)));
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeStartPlay(JNIEnv*, jobject, jlong handle)
{
    auto* s = state(handle);
    const auto result = s->pet.startPlay();
    s->rpsStarted = result == vaiwu::game::ActionResult::Ok;
    return static_cast<jint>(result);
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeCancelPlay(JNIEnv*, jobject, jlong handle)
{
    state(handle)->rpsStarted = false;
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativePlayRps(JNIEnv* env, jobject, jlong handle, jint userChoice)
{
    auto* s = state(handle);
    if (!s->rpsStarted || s->pet.life() != vaiwu::game::LifeState::Awake ||
        userChoice < 0 || userChoice > 2) {
        return nullptr;
    }
    s->rpsStarted = false;
    const int petChoice = std::uniform_int_distribution<int>(0, 2)(s->rng);
    const int difference = (userChoice - petChoice + 3) % 3;
    const int outcome = difference == 0 ? 0 : difference == 1 ? 1 : -1;
    const int reward = outcome > 0 ? 2 : 0;
    if (outcome > 0) {
        s->pet.applyPlayResult(true);
        s->pet.addCoins(reward);
    } else if (outcome < 0) {
        s->pet.applyPlayResult(false);
    } else {
        s->pet.giveAffection(3.0f);
    }
    const jclass resultClass = env->FindClass("su/neit/vaiwu/RpsResult");
    if (resultClass == nullptr) {
        return nullptr;
    }
    const jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(III)V");
    if (constructor == nullptr) {
        return nullptr;
    }
    return env->NewObject(resultClass, constructor,
        static_cast<jint>(petChoice), static_cast<jint>(outcome), static_cast<jint>(reward));
}
