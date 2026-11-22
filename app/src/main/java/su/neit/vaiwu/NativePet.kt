package su.neit.vaiwu

import java.io.Closeable

/**
 * Thread-safe JNI wrapper around the native C++ `VaiwuPet` + RPG state
 * (`app/src/main/cpp/native-lib.cpp`, `battle-lib.cpp`, `info-lib.cpp`).
 *
 * All methods synchronize on `this` and guard against calls after [close].
 */
class NativePet : Closeable {
    private var handle: Long = nativeCreate()

    private fun requireHandle(): Long {
        check(handle != InvalidHandle) { "NativePet has been closed" }
        return handle
    }

    @Synchronized
    fun startNew(characterId: Int, name: String, nowMs: Long) {
        if (handle == 0L) return
        nativeStartNew(handle, characterId, name, nowMs)
    }

    @Synchronized
    fun load(data: String, nowMs: Long): Boolean {
        if (handle == 0L) return false
        return nativeLoad(handle, data, nowMs)
    }

    @Synchronized
    fun save(): String {
        if (handle == 0L) return ""
        return nativeSave(handle)
    }

    @Synchronized
    fun tick(nowMs: Long) {
        if (handle == 0L) return
        nativeTick(handle, nowMs)
    }

    @Synchronized
    fun action(action: Int): Int {
        if (handle == 0L) return -1
        return nativeAction(handle, action)
    }

    @Synchronized
    fun snapshot(): PetSnapshot = nativeSnapshot(requireHandle())

    @Synchronized
    fun shop(): Array<ShopItemSnapshot> {
        if (handle == 0L) return emptyArray()
        return nativeShop(handle)
    }

    @Synchronized
    fun buy(itemId: Int): Boolean {
        if (handle == 0L) return false
        return nativeBuy(handle, itemId)
    }

    @Synchronized
    fun useItem(itemId: Int): Int {
        if (handle == 0L) return UseResult.NotOwned
        return nativeUseItem(handle, itemId)
    }

    /** Feed: `itemId < 0` is free rice, otherwise a dish from the feed menu. */
    @Synchronized
    fun feedWith(itemId: Int): Int {
        if (handle == 0L) return UseResult.NotApplicable
        return nativeFeedWith(handle, itemId)
    }

    @Synchronized
    fun inventory(): InventorySnapshot = nativeInventory(requireHandle())

    @Synchronized
    fun equip(slot: Int, itemId: Int): Boolean {
        if (handle == 0L) return false
        return nativeEquip(handle, slot, itemId)
    }

    @Synchronized
    fun sell(itemId: Int): Int {
        if (handle == 0L) return 0
        return nativeSell(handle, itemId)
    }

    @Synchronized
    fun startPlay(): Int {
        if (handle == 0L) return -1
        return nativeStartPlay(handle)
    }

    @Synchronized
    fun cancelPlay() {
        if (handle == 0L) return
        nativeCancelPlay(handle)
    }

    @Synchronized
    fun playRps(choice: Int): RpsResult? {
        if (handle == 0L) return null
        return nativePlayRps(handle, choice)
    }

    @Synchronized
    fun finishGame(outcome: Int, reward: Int): Boolean {
        if (handle == 0L) return false
        return nativeFinishGame(handle, outcome, reward)
    }

    @Synchronized
    fun startBattle(dungeon: Boolean): Int {
        if (handle == 0L) return -1
        return nativeStartBattle(handle, dungeon)
    }

    @Synchronized
    fun battleSnapshot(): BattleSnapshot? {
        if (handle == 0L) return null
        return nativeBattleSnapshot(handle)
    }

    @Synchronized
    fun battleAction(action: Int): BattleSnapshot? {
        if (handle == 0L) return null
        return nativeBattleAction(handle, action)
    }

    @Synchronized
    fun nextDungeonRoom(): Boolean {
        if (handle == 0L) return false
        return nativeDungeonNext(handle)
    }

    @Synchronized
    fun leaveBattle() {
        if (handle == 0L) return
        nativeLeaveBattle(handle)
    }

    @Synchronized
    fun allocateStat(stat: Int) {
        if (handle == 0L) return
        nativeAllocateStat(handle, stat)
    }

    @Synchronized
    fun settleTacticalBattle(
        outcome: Int,
        hpLeft: Int,
        mpLeft: Int,
        potionsLeft: Int,
        mpPotionsLeft: Int,
        xpGained: Int,
        goldDelta: Int,
        applyPlayOutcome: Boolean,
    ): Int {
        if (handle == 0L) return -1
        return nativeSettleTacticalBattle(
            handle,
            outcome,
            hpLeft,
            mpLeft,
            potionsLeft,
            mpPotionsLeft,
            xpGained,
            goldDelta,
            applyPlayOutcome,
        )
    }

    @Synchronized
    fun restDungeonRoom(): Boolean {
        if (handle == 0L) return false
        return nativeRestDungeonRoom(handle)
    }

    @Synchronized
    fun phrase(context: Int): String? {
        if (handle == 0L) return null
        return nativePhrase(handle, context)
    }

    @Synchronized
    fun characterProfile(characterId: Int): CharacterProfile? {
        if (handle == 0L) return null
        return nativeCharacterProfile(handle, characterId)
    }

    @Synchronized
    fun parseIntent(text: String): AssistantIntent? {
        if (handle == 0L) return null
        return nativeParseIntent(handle, text)
    }

    @Synchronized
    fun joke(): String {
        if (handle == 0L) return ""
        return nativeJoke(handle)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStartNew(handle: Long, characterId: Int, name: String, nowMs: Long)
    private external fun nativeLoad(handle: Long, data: String, nowMs: Long): Boolean
    private external fun nativeSave(handle: Long): String
    private external fun nativeTick(handle: Long, nowMs: Long)
    private external fun nativeAction(handle: Long, action: Int): Int
    private external fun nativeSnapshot(handle: Long): PetSnapshot
    private external fun nativeShop(handle: Long): Array<ShopItemSnapshot>
    private external fun nativeBuy(handle: Long, itemId: Int): Boolean
    private external fun nativeUseItem(handle: Long, itemId: Int): Int
    private external fun nativeFeedWith(handle: Long, itemId: Int): Int
    private external fun nativeInventory(handle: Long): InventorySnapshot
    private external fun nativeEquip(handle: Long, slot: Int, itemId: Int): Boolean
    private external fun nativeSell(handle: Long, itemId: Int): Int
    private external fun nativeStartPlay(handle: Long): Int
    private external fun nativeCancelPlay(handle: Long)
    private external fun nativePlayRps(handle: Long, choice: Int): RpsResult?
    private external fun nativeFinishGame(handle: Long, outcome: Int, reward: Int): Boolean
    private external fun nativeStartBattle(handle: Long, dungeon: Boolean): Int
    private external fun nativeBattleSnapshot(handle: Long): BattleSnapshot?
    private external fun nativeBattleAction(handle: Long, action: Int): BattleSnapshot?
    private external fun nativeDungeonNext(handle: Long): Boolean
    private external fun nativeLeaveBattle(handle: Long)
    private external fun nativeAllocateStat(handle: Long, stat: Int)
    private external fun nativeSettleTacticalBattle(
        handle: Long,
        outcome: Int,
        hpLeft: Int,
        mpLeft: Int,
        potionsLeft: Int,
        mpPotionsLeft: Int,
        xpGained: Int,
        goldDelta: Int,
        applyPlayOutcome: Boolean,
    ): Int
    private external fun nativeRestDungeonRoom(handle: Long): Boolean
    private external fun nativePhrase(handle: Long, context: Int): String?
    private external fun nativeCharacterProfile(handle: Long, characterId: Int): CharacterProfile?
    private external fun nativeParseIntent(handle: Long, text: String): AssistantIntent?
    private external fun nativeJoke(handle: Long): String

    companion object {
        /** Sentinel handle value after [close] destroys the native C++ state. */
        private const val InvalidHandle = 0L

        init {
            System.loadLibrary("vaiwu_native")
        }
    }
}
