#include <jni.h>

#include "JniHelpers.hpp"
#include "NativeState.hpp"

namespace {

// Number of valid legacy RpgAction enum values (Attack, Skill, Potion, MpPotion, Run).
constexpr jint kRpgActionCount = 5;

// Safely unwraps an opaque JNI handle back into its NativePetState pointer.
NativePetState* state(jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    return reinterpret_cast<NativePetState*>(static_cast<intptr_t>(handle));
}

jstring javaString(JNIEnv* env, const std::string& value)
{
    return vaiwu::android::toJavaString(env, value);
}

vaiwu::game::Str monsterName(vaiwu::game::MonsterKind kind)
{
    using namespace vaiwu::game;
    switch (kind) {
    case MonsterKind::Bat: return Str::MonBat;
    case MonsterKind::Shroom: return Str::MonShroom;
    case MonsterKind::Fox: return Str::MonFox;
    case MonsterKind::Ghost: return Str::MonGhost;
    case MonsterKind::Golem: return Str::MonGolem;
    default: return Str::MonSlime;
    }
}

jobject battleSnapshot(JNIEnv* env, NativePetState* native)
{
    try {
        if (env == nullptr || env->ExceptionCheck() || native == nullptr) {
            return nullptr;
        }
        using namespace vaiwu::game;
        const auto& battle = native->battle.state();
        if (!battle.active) {
            return nullptr;
        }
        const auto& pet = native->pet;
        const auto& progress = pet.rpg();
        const jclass snapshotClass = env->FindClass("su/neit/vaiwu/BattleSnapshot");
        if (snapshotClass == nullptr || env->ExceptionCheck()) {
            return nullptr;
        }
        const jmethodID constructor = env->GetMethodID(snapshotClass, "<init>",
            "(ZZZZZZ" "IIIIII" "IIIIII" "IIIIII"
            "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        if (constructor == nullptr || env->ExceptionCheck()) {
            env->DeleteLocalRef(snapshotClass);
            return nullptr;
        }
        jstring foeNameStr = javaString(env, tr(Lang::Ru, monsterName(battle.foe.kind)));
        jstring logStr = javaString(env, battle.log);
        jstring skillStr = javaString(env, tr(Lang::Ru, skillNameFor(pet.character())));
        jobject snapshot = nullptr;
        if (foeNameStr != nullptr && logStr != nullptr && skillStr != nullptr && !env->ExceptionCheck()) {
            snapshot = env->NewObject(snapshotClass, constructor,
                battle.active ? JNI_TRUE : JNI_FALSE,
                battle.over ? JNI_TRUE : JNI_FALSE,
                battle.dungeon.active ? JNI_TRUE : JNI_FALSE,
                battle.awaitingRest ? JNI_TRUE : JNI_FALSE,
                battle.cleared ? JNI_TRUE : JNI_FALSE,
                battle.dungeon.active && isBossRoom(battle.dungeon) ? JNI_TRUE : JNI_FALSE,
                static_cast<jint>(battle.dungeon.active ? battle.dungeon.room + 1 : 0),
                static_cast<jint>(battle.dungeon.rooms),
                static_cast<jint>(battle.foe.kind),
                static_cast<jint>(battle.foe.level),
                static_cast<jint>(battle.foe.hp),
                static_cast<jint>(battle.foe.hpMax),
                static_cast<jint>(progress.level),
                static_cast<jint>(progress.hp),
                static_cast<jint>(hpMaxFor(pet.character(), progress)),
                static_cast<jint>(progress.mp),
                static_cast<jint>(mpMaxFor(pet.character(), progress)),
                static_cast<jint>(progress.potions),
                static_cast<jint>(progress.mpPotions),
                static_cast<jint>(progress.xp),
                static_cast<jint>(xpForNext(progress.level)),
                static_cast<jint>(progress.statPoints),
                static_cast<jint>(battle.outcome),
                static_cast<jint>(battle.chestGold),
                foeNameStr,
                logStr,
                skillStr);
        }
        if (foeNameStr != nullptr) env->DeleteLocalRef(foeNameStr);
        if (logStr != nullptr) env->DeleteLocalRef(logStr);
        if (skillStr != nullptr) env->DeleteLocalRef(skillStr);
        env->DeleteLocalRef(snapshotClass);
        return snapshot;
    } catch (...) {
        return nullptr;
    }
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeStartBattle(
    JNIEnv*, jobject, jlong handle, jboolean dungeon)
{
    try {
        auto* native = state(handle);
        if (native == nullptr) {
            return -1;
        }
        return static_cast<jint>(native->battle.start(native->pet, dungeon == JNI_TRUE, native->rng()));
    } catch (...) {
        return -1;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeBattleSnapshot(JNIEnv* env, jobject, jlong handle)
{
    try {
        return battleSnapshot(env, state(handle));
    } catch (...) {
        return nullptr;
    }
}

// Executes one legacy turn-based battle action after validating the action index.
extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeBattleAction(JNIEnv* env, jobject, jlong handle, jint action)
{
    try {
        auto* native = state(handle);
        if (native == nullptr || action < 0 || action >= kRpgActionCount) {
            return nullptr;
        }
        native->battle.takeTurn(native->pet, static_cast<vaiwu::game::RpgAction>(action), native->rng());
        return battleSnapshot(env, native);
    } catch (...) {
        return nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeDungeonNext(JNIEnv*, jobject, jlong handle)
{
    try {
        auto* native = state(handle);
        if (native == nullptr) {
            return JNI_FALSE;
        }
        return native->battle.nextRoom(native->pet, native->rng()) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeLeaveBattle(JNIEnv*, jobject, jlong handle)
{
    try {
        if (auto* native = state(handle)) {
            native->battle.leave();
        }
    } catch (...) {}
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeAllocateStat(JNIEnv*, jobject, jlong handle, jint stat)
{
    try {
        if (auto* native = state(handle)) {
            native->battle.allocate(native->pet, stat);
        }
    } catch (...) {}
}

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeSettleTacticalBattle(
    JNIEnv*, jobject, jlong handle,
    jint outcome, jint hpLeft, jint mpLeft,
    jint potionsLeft, jint mpPotionsLeft,
    jint xpGained, jint goldDelta, jboolean applyPlayOutcome)
{
    try {
        auto* native = state(handle);
        if (native == nullptr) {
            return -1;
        }
        vaiwu::android::TacticalSettlement settlement{};
        settlement.outcome = static_cast<int>(outcome);
        settlement.hpLeft = static_cast<int>(hpLeft);
        settlement.mpLeft = static_cast<int>(mpLeft);
        settlement.potionsLeft = static_cast<int>(potionsLeft);
        settlement.mpPotionsLeft = static_cast<int>(mpPotionsLeft);
        settlement.xpGained = static_cast<int>(xpGained);
        settlement.goldDelta = static_cast<int>(goldDelta);
        settlement.applyPlayOutcome = applyPlayOutcome == JNI_TRUE;
        return static_cast<jint>(vaiwu::android::settleTacticalBattle(native->pet, settlement));
    } catch (...) {
        return -1;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeRestDungeonRoom(JNIEnv*, jobject, jlong handle)
{
    try {
        auto* native = state(handle);
        if (native == nullptr) {
            return JNI_FALSE;
        }
        return vaiwu::android::restBetweenTacticalRooms(native->pet) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}
