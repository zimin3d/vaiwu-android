#include <jni.h>

#include "NativeState.hpp"

namespace {

NativePetState* state(jlong handle)
{
    return reinterpret_cast<NativePetState*>(static_cast<intptr_t>(handle));
}

jstring javaString(JNIEnv* env, const std::string& value)
{
    return env->NewStringUTF(value.c_str());
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
    using namespace vaiwu::game;
    const auto& battle = native->battle.state();
    if (!battle.active) {
        return nullptr;
    }
    const auto& pet = native->pet;
    const auto& progress = pet.rpg();
    const jclass snapshotClass = env->FindClass("su/neit/vaiwu/BattleSnapshot");
    if (snapshotClass == nullptr) {
        return nullptr;
    }
    const jmethodID constructor = env->GetMethodID(snapshotClass, "<init>",
        "(ZZZZZZ" "IIIIII" "IIIIII" "IIIIII"
        "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    if (constructor == nullptr) {
        return nullptr;
    }
    return env->NewObject(snapshotClass, constructor,
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
        javaString(env, tr(Lang::Ru, monsterName(battle.foe.kind))),
        javaString(env, battle.log),
        javaString(env, tr(Lang::Ru, skillNameFor(pet.character()))));
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_su_neit_vaiwu_NativePet_nativeStartBattle(
    JNIEnv*, jobject, jlong handle, jboolean dungeon)
{
    auto* native = state(handle);
    return static_cast<jint>(native->battle.start(native->pet, dungeon == JNI_TRUE, native->rng()));
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeBattleSnapshot(JNIEnv* env, jobject, jlong handle)
{
    return battleSnapshot(env, state(handle));
}

extern "C" JNIEXPORT jobject JNICALL
Java_su_neit_vaiwu_NativePet_nativeBattleAction(JNIEnv* env, jobject, jlong handle, jint action)
{
    if (action < 0 || action >= 5) {
        return nullptr;
    }
    auto* native = state(handle);
    native->battle.takeTurn(native->pet, static_cast<vaiwu::game::RpgAction>(action), native->rng());
    return battleSnapshot(env, native);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_su_neit_vaiwu_NativePet_nativeDungeonNext(JNIEnv*, jobject, jlong handle)
{
    auto* native = state(handle);
    return native->battle.nextRoom(native->pet, native->rng()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeLeaveBattle(JNIEnv*, jobject, jlong handle)
{
    state(handle)->battle.leave();
}

extern "C" JNIEXPORT void JNICALL
Java_su_neit_vaiwu_NativePet_nativeAllocateStat(JNIEnv*, jobject, jlong handle, jint stat)
{
    auto* native = state(handle);
    native->battle.allocate(native->pet, stat);
}
