package su.neit.vaiwu

data class PetSnapshot(
    val characterId: Int,
    val characterName: String,
    val petName: String,
    val hunger: Float,
    val happiness: Float,
    val energy: Float,
    val discipline: Float,
    val weightKg: Float,
    val ageSeconds: Int,
    val coins: Int,
    val emotionId: Int,
    val lifeState: Int,
    val isSick: Boolean,
)

val PetSnapshot.isAlive: Boolean get() = lifeState != LifeState.Dead
val PetSnapshot.isSleeping: Boolean get() = lifeState == LifeState.Sleeping
val PetSnapshot.canSleep: Boolean get() = isAlive && !isSleeping && energy < MANUAL_SLEEP_MAX_ENERGY
val PetSnapshot.canPlay: Boolean get() = isAlive && !isSleeping && energy > PLAY_MIN_ENERGY_THRESHOLD
