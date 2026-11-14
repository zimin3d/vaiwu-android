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
