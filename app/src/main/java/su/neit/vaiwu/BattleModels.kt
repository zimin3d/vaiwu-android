package su.neit.vaiwu

data class BattleSnapshot(
    val active: Boolean,
    val over: Boolean,
    val dungeon: Boolean,
    val awaitingRest: Boolean,
    val cleared: Boolean,
    val boss: Boolean,
    val room: Int,
    val rooms: Int,
    val foeKind: Int,
    val foeLevel: Int,
    val foeHp: Int,
    val foeHpMax: Int,
    val level: Int,
    val hp: Int,
    val hpMax: Int,
    val mp: Int,
    val mpMax: Int,
    val potions: Int,
    val mpPotions: Int,
    val xp: Int,
    val xpNext: Int,
    val statPoints: Int,
    val outcome: Int,
    val chestGold: Int,
    val foeName: String,
    val log: String,
    val skillName: String,
)

object RpgAction {
    const val Attack = 0
    const val Skill = 1
    const val Potion = 2
    const val Run = 3
    const val MpPotion = 4
}

object BattleOutcome {
    const val Ongoing = 0
    const val Victory = 1
    const val Defeat = -1
    const val Escaped = 2
}
