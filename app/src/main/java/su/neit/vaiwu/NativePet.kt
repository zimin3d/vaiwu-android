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

    companion object {
        init {
            System.loadLibrary("vaiwu_native")
        }
    }
}
