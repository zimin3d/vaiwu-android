package su.neit.vaiwu

import java.io.Closeable

class NativePet : Closeable {
    private var handle: Long = nativeCreate()

    @Synchronized
    fun startNew(characterId: Int, name: String, nowMs: Long) {
        nativeStartNew(handle, characterId, name, nowMs)
    }

    @Synchronized
    fun load(data: String, nowMs: Long): Boolean = nativeLoad(handle, data, nowMs)

    @Synchronized
    fun save(): String = nativeSave(handle)

    @Synchronized
    fun tick(nowMs: Long) = nativeTick(handle, nowMs)

    @Synchronized
    fun action(action: Int): Int = nativeAction(handle, action)

    @Synchronized
    fun snapshot(): PetSnapshot = nativeSnapshot(handle)

    @Synchronized
    fun shop(): Array<ShopItemSnapshot> = nativeShop(handle)

    @Synchronized
    fun buy(itemId: Int): Boolean = nativeBuy(handle, itemId)

    @Synchronized
    fun inventory(): InventorySnapshot = nativeInventory(handle)

    @Synchronized
    fun equip(slot: Int, itemId: Int) = nativeEquip(handle, slot, itemId)

    @Synchronized
    fun sell(itemId: Int): Int = nativeSell(handle, itemId)

    @Synchronized
    fun startPlay(): Int = nativeStartPlay(handle)

    @Synchronized
    fun playRps(choice: Int): RpsResult = nativePlayRps(handle, choice)

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
    private external fun nativeInventory(handle: Long): InventorySnapshot
    private external fun nativeEquip(handle: Long, slot: Int, itemId: Int)
    private external fun nativeSell(handle: Long, itemId: Int): Int
    private external fun nativeStartPlay(handle: Long): Int
    private external fun nativePlayRps(handle: Long, choice: Int): RpsResult

    companion object {
        init {
            System.loadLibrary("vaiwu_native")
        }
    }
}
