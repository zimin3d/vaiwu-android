package su.neit.vaiwu.combat

/** Action points granted to each living, unstunned combatant at the start of their turn. */
const val AP_PER_TURN = 3

/** Damage multiplier for ranged weapon attacks when the target stands behind cover (50% reduction). */
const val COVER_WEAPON = 0.5

/** Damage multiplier for ranged spells when the target stands behind cover (30% reduction). */
const val COVER_SPELL = 0.7

/** Damage multiplier applied against a target marked by an enemy pack's Command knack (+30% damage). */
const val COMMAND_BONUS = 1.3

/** Damage multiplier for ranged hits against a veiled target (Smoke Veil halves incoming ranged hits). */
const val VEIL_FACTOR = 0.5

/** Action point cost for an enemy Shroom's ally-healing Mend knack. */
const val MEND_AP = 2

/** Maximum Chebyshev distance (in cells) at which a Shroom can heal a wounded ally. */
const val MEND_RANGE = 4

/** Ally HP fraction below which a Shroom considers them wounded enough to Mend (70% HP). */
const val MEND_THRESHOLD = 0.7

/** Action point cost for an enemy Fox's pack-marking Command knack. */
const val COMMAND_AP = 1

/** Action point cost to drink an HP or MP potion during battle. */
const val POTION_AP = 1

/** Action point cost to attempt fleeing the arena. */
const val FLEE_AP = 2

/** Fraction of max HP restored by a single HP potion (50%). */
const val POTION_HEAL_RATIO = 0.5

/** Fraction of max MP restored by a single MP potion (50%). */
const val POTION_MP_RATIO = 0.5

/** Number of turns of stun immunity ("Steeled") granted after recovering from a stun. */
const val STEELED_TURNS = 2

/** Stable combatant id for the player's heroine across all tactical battles. */
const val PLAYER_ID = "player"

enum class Side {
    Player,
    Enemy,
}

enum class AiRole {
    Melee,
    Ranged,
    Caster,
}

enum class EnemyKnack {
    Mend,
    Command,
}

enum class SkillEffect {
    Attack,
    Control,
    Defense,
    Heal,
    Teleport,
    Veil,
}

enum class MonsterKind(val nativeId: Int) {
    Slime(0),
    Bat(1),
    Shroom(2),
    Fox(3),
    Ghost(4),
    Golem(5),
    Salamander(6),
    Moth(7),
    Mimic(8),
    Automaton(9);

    companion object {
        fun fromNative(id: Int): MonsterKind =
            entries.firstOrNull { it.nativeId == id } ?: Slime
    }
}

enum class WeaponId(val equipItemId: Int) {
    Fists(-1),
    WoodenSword(0),
    SteelSword(1),
    MagicStaff(2),
    WindDagger(3),
    MoonBlade(8);

    companion object {
        fun fromEquipId(id: Int): WeaponId =
            entries.firstOrNull { it.equipItemId == id } ?: Fists
    }
}

enum class SkillId {
    // Aoi
    AoiFlash,
    AoiIceBind,
    AoiMirrorShield,
    // Hina
    HinaFlurry,
    HinaWindGust,
    HinaGaleBarrier,
    // Rin
    RinPreciseThrust,
    RinFlameSlash,
    RinFireShield,
    // Yuki
    YukiDrain,
    YukiFrostRay,
    YukiIceShield,
    // Akane
    AkaneLuckyStrike,
    AkaneSmokeVeil,
    AkaneWindBarrier,
    // Uni
    UniHeal,
    UniLightRay,
    UniLightShield,
    // Kolobok
    KolobokStoneRam,
    KolobokEarthTremor,
    KolobokHardCrust,
    // Vesper
    VesperDarkBolt,
    VesperShadowStep,
    VesperShadowShroud,
    // Monsters
    MonsterSlimeSpit,
    MonsterBatScreech,
    MonsterShroomSpores,
    MonsterFoxShot,
    MonsterGhostBolt,
    MonsterGhostShield,
    MonsterGolemSlam,
    MonsterGolemShield,
    MonsterSalamanderEmber,
    MonsterMothPrism,
    MonsterMothBlink,
    MonsterMothVeil,
    MonsterMimicBite,
    MonsterAutomatonGale,
    MonsterAutomatonAegis,
}

data class TacticalSkill(
    val id: SkillId,
    val element: Element,
    val effect: SkillEffect,
    val apCost: Int,
    val mpCost: Int,
    val range: Int,
    val power: Int,
    val penetration: Double = 0.0,
    val hits: Int = 1,
    val guaranteedCrit: Boolean = false,
    val ignoresCover: Boolean = false,
    val burnAmount: Int = 0,
    val poisonTurns: Int = 0,
    val poisonDamage: Int = 0,
    val stunChance: Double = 0.0,
    val lifestealRatio: Double = 0.0,
    val stealGold: Boolean = false,
    val shieldDecay: Double = 0.25,
    val pushCells: Int = 0,
    val cleanseDots: Boolean = false,
    val bonusVsDark: Double = 1.0,
)

data class TacticalWeapon(
    val id: WeaponId,
    val minDamage: Int,
    val maxDamage: Int,
    val apCost: Int = 1,
    val range: Int = 1,
    val poisonChance: Double = 0.0,
    val poisonDamage: Int = 3,
    val critChance: Double = 0.08,
)

data class ShieldState(
    val value: Double,
    val element: Element,
    val decay: Double = 0.25,
)

data class Combatant(
    val id: String,
    val side: Side,
    val characterId: Int = -1,
    val monsterKind: MonsterKind? = null,
    val isBoss: Boolean = false,
    val customName: String = "",
    val level: Int = 1,
    val maxHp: Int,
    val hp: Int,
    val maxMp: Int = 0,
    val mp: Int = 0,
    val mpRegen: Int = 0,
    val ap: Int = AP_PER_TURN,
    val cellsPerAp: Int = CELLS_PER_AP,
    val armor: Int = 0,
    val agility: Int = 2,
    val luck: Int = 2,
    val shield: ShieldState? = null,
    val stunned: Int = 0,
    val steeled: Int = 0,
    val burn: Int = 0,
    val poisonTurns: Int = 0,
    val poisonDamage: Int = 0,
    val marked: Int = 0,
    val veiled: Int = 0,
    val poisonImmune: Boolean = false,
    val skills: List<TacticalSkill> = emptyList(),
    val weapon: TacticalWeapon,
    val pos: Pos,
    val aiRole: AiRole = AiRole.Melee,
    val knack: EnemyKnack? = null,
    val stealGoldOnHit: Boolean = false,
    val xpReward: Int = 0,
    val goldReward: Int = 0,
) {
    val isAlive: Boolean get() = hp > 0
}

sealed interface CombatAction {
    data class Move(val to: Pos) : CombatAction
    data class WeaponAttack(val targetId: String) : CombatAction
    data class UseSkill(
        val skillId: SkillId,
        val targetId: String? = null,
        val targetPos: Pos? = null,
    ) : CombatAction
    data object UseHpPotion : CombatAction
    data object UseMpPotion : CombatAction
    data object Flee : CombatAction
    data object EndTurn : CombatAction
}

enum class DamageKind {
    Hit,
    Burn,
    Poison,
}

enum class StatusTag {
    Burn,
    Poison,
    Stunned,
    Steeled,
    Veiled,
    Marked,
    Cleanse,
    Pushed,
    StolenGold,
}

sealed interface CombatEvent {
    val id: Int

    data class Move(
        override val id: Int,
        val actorId: String,
        val from: Pos,
        val to: Pos,
        val path: List<Pos>,
        val teleport: Boolean = false,
    ) : CombatEvent

    data class SkillCast(
        override val id: Int,
        val actorId: String,
        val targetId: String?,
        val skillId: SkillId,
        val element: Element,
        val effect: SkillEffect,
        val power: Int,
    ) : CombatEvent

    data class WeaponStrike(
        override val id: Int,
        val actorId: String,
        val targetId: String,
        val weaponId: WeaponId,
        val melee: Boolean,
    ) : CombatEvent

    data class ShieldUp(
        override val id: Int,
        val actorId: String,
        val element: Element,
        val value: Int,
        val skillId: SkillId,
    ) : CombatEvent

    data class Blocked(
        override val id: Int,
        val targetId: String,
    ) : CombatEvent

    data class Damage(
        override val id: Int,
        val targetId: String,
        val amount: Int,
        val kind: DamageKind,
        val crit: Boolean = false,
    ) : CombatEvent

    data class Heal(
        override val id: Int,
        val actorId: String,
        val targetId: String,
        val amount: Int,
    ) : CombatEvent

    data class MpRestore(
        override val id: Int,
        val targetId: String,
        val amount: Int,
    ) : CombatEvent

    data class StatusApplied(
        override val id: Int,
        val targetId: String,
        val tag: StatusTag,
        val amount: Int = 0,
    ) : CombatEvent

    data class CommandMark(
        override val id: Int,
        val actorId: String,
        val targetId: String,
    ) : CombatEvent

    data class Down(
        override val id: Int,
        val targetId: String,
    ) : CombatEvent

    data class FleeAttempt(
        override val id: Int,
        val actorId: String,
        val success: Boolean,
    ) : CombatEvent
}

/** Structured, locale-free log entries formatted via `strings.xml` in the UI layer. */
sealed interface CombatLogEntry {
    data object BattleStarted : CombatLogEntry
    data class Moved(val actorId: String, val teleport: Boolean) : CombatLogEntry
    data class WeaponHit(val actorId: String, val targetId: String, val weaponId: WeaponId, val covered: Boolean) : CombatLogEntry
    data class SkillUsed(val actorId: String, val targetId: String?, val skillId: SkillId, val covered: Boolean) : CombatLogEntry
    data class ShieldRaised(val actorId: String, val skillId: SkillId, val value: Int) : CombatLogEntry
    data class ShieldBroken(val targetId: String) : CombatLogEntry
    data class ShieldHeld(val targetId: String) : CombatLogEntry
    data class DamageTaken(val targetId: String, val amount: Int, val kind: DamageKind, val crit: Boolean) : CombatLogEntry
    data class Healed(val actorId: String, val targetId: String, val amount: Int) : CombatLogEntry
    data class MpRestored(val targetId: String, val amount: Int) : CombatLogEntry
    data class StatusChanged(val targetId: String, val tag: StatusTag, val amount: Int = 0) : CombatLogEntry
    data class StunSkippedTurn(val actorId: String) : CombatLogEntry
    data class Commanded(val actorId: String, val targetId: String) : CombatLogEntry
    data class GoldChanged(val actorId: String, val delta: Int) : CombatLogEntry
    data class Defeated(val targetId: String) : CombatLogEntry
    data class FleeResult(val success: Boolean) : CombatLogEntry
    data class BattleEnded(val winner: CombatWinner) : CombatLogEntry
}

enum class CombatWinner {
    Player,
    Enemy,
    Escaped,
}

enum class ActionFailReason {
    BattleOver,
    NotEnoughAp,
    NotEnoughMp,
    OutOfRange,
    OutOfBounds,
    TileBlocked,
    NoPath,
    InvalidTarget,
    SkillNotFound,
    NoPotion,
    AlreadyFull,
    AlreadyVeiled,
}

class CombatActionException(val reason: ActionFailReason) : IllegalStateException(reason.name)

data class CombatState(
    val grid: TacticalGrid,
    val combatants: List<Combatant>,
    val activeIndex: Int,
    val round: Int,
    val events: List<CombatEvent>,
    val eventSeq: Int,
    val log: List<CombatLogEntry>,
    val rng: SeededRng,
    val winner: CombatWinner? = null,
    val hpPotions: Int = 0,
    val mpPotions: Int = 0,
    val goldDelta: Int = 0,
    val xpEarned: Int = 0,
) {
    val activeCombatant: Combatant get() = combatants[activeIndex]
    val player: Combatant get() = combatants.first { it.id == PLAYER_ID }
    val aliveEnemies: List<Combatant> get() = combatants.filter { it.side == Side.Enemy && it.isAlive }
    val isPlayerTurn: Boolean get() = winner == null && activeCombatant.side == Side.Player
}

enum class IntentKind {
    Command,
    Mend,
    Attack,
    Control,
    Shield,
    Cover,
    Approach,
    Stunned,
    Idle,
}

data class EnemyIntent(
    val actorId: String,
    val kind: IntentKind,
    val targetId: String? = null,
    val skillId: SkillId? = null,
    val weaponAttack: Boolean = false,
    val melee: Boolean = false,
    val expectedDamage: Int? = null,
    val moves: Boolean = false,
)

data class AttackPreview(
    val inRange: Boolean,
    val covered: Boolean,
    val damage: Int,
    val blocked: Boolean,
    val kills: Boolean,
)

sealed interface ForecastSource {
    data class Weapon(val weaponId: WeaponId) : ForecastSource
    data class Skill(val skillId: SkillId) : ForecastSource
}

data class ForecastLine(
    val source: ForecastSource,
    val damage: Int,
    val inRange: Boolean,
    val covered: Boolean,
    val blocked: Boolean,
    val kills: Boolean,
)

/**
 * Full session state for an Android tactical Adventure or Dungeon run.
 */
data class TacticalBattleSession(
    val dungeon: Boolean,
    val room: Int,
    val rooms: Int,
    val isBossRoom: Boolean,
    val state: CombatState,
    val undoStack: List<CombatState> = emptyList(),
    val awaitingRest: Boolean = false,
    val dungeonCleared: Boolean = false,
    val chestGold: Int = 0,
    val bankedGold: Int = 0,
    val bankedXp: Int = 0,
    val totalXpThisRoom: Int = 0,
    val totalGoldThisRoom: Int = 0,
    val levelsGainedThisRoom: Int = 0,
    val settled: Boolean = false,
) {
    val isOver: Boolean get() = state.winner != null
}
