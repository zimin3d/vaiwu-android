package su.neit.vaiwu.combat

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Which UI control or arena tile the tactical coach highlights.
 */
sealed interface CoachPick {
    data class Skill(val skillId: SkillId) : CoachPick
    data object Weapon : CoachPick
    data object HpPotion : CoachPick
    data object MpPotion : CoachPick
    data class Field(val to: Pos? = null) : CoachPick
    data object EndTurn : CoachPick
}

/**
 * Structured, locale-free advice key formatted via `strings.xml` in the UI layer.
 */
sealed interface CoachMessage {
    data class FinishTarget(val targetId: String, val targetHp: Int, val skillId: SkillId?) : CoachMessage
    data class FocusFinish(val targetId: String, val targetHp: Int, val skillId: SkillId?) : CoachMessage
    data class DrinkHpPotion(val incoming: Int, val hp: Int, val lethal: Boolean) : CoachMessage
    data class DrinkMpPotion(val mp: Int, val maxMp: Int) : CoachMessage
    data class FocusHealer(val healerId: String) : CoachMessage
    data class DodgeToCover(val threatId: String, val incoming: Int, val after: Int) : CoachMessage
    data class RaiseShield(val threatId: String, val incoming: Int, val saved: Int, val skillId: SkillId, val lethal: Boolean) : CoachMessage
    data class UseControl(val targetId: String, val skillId: SkillId) : CoachMessage
    data class CastAttack(val targetId: String, val skillId: SkillId, val damage: Int, val blocked: Boolean) : CoachMessage
    data class StrikeWeapon(val targetId: String) : CoachMessage
    data class SwitchToReachable(val targetId: String, val otherId: String) : CoachMessage
    data class ApproachTarget(val targetId: String, val distance: Int) : CoachMessage
    data class PreemptiveShield(val targetId: String, val skillId: SkillId) : CoachMessage
    data object EndTurnAdvice : CoachMessage
}

data class CoachHint(
    val message: CoachMessage,
    val pick: CoachPick,
    val focusEnemyId: String? = null,
    val showIntro: Boolean = false,
)

/**
 * Tactical coach heuristic thresholds. Tuning these adjusts how conservative
 * or aggressive the coach's recommendations are without changing combat rules.
 */
private const val HEAL_HP_RATIO_LETHAL = 0.60      // Recommend emergency heal below 60% HP when facing lethal damage.
private const val HEAL_HP_RATIO_NORMAL = 0.35      // Recommend proactive heal below 35% HP even when not lethal.
private const val DODGE_INCOMING_HP_RATIO = 0.25   // Consider stepping to cover when incoming damage >= 25% max HP.
private const val DODGE_MIN_SAVED_HP = 4           // Minimum flat HP a dodge step must save to be worth 1 AP.
private const val DODGE_MIN_SAVED_RATIO = 0.20     // Minimum fraction of max HP a dodge step must save.
private const val SHIELD_LOW_HP_RATIO = 0.50       // Below 50% HP, lower the bar for raising a shield.
private const val SHIELD_MIN_SAVED_LETHAL = 3      // Minimum HP saved for a lethal-preventing shield suggestion.
private const val SHIELD_MIN_SAVED_LOW_HP = 4      // Minimum HP saved to suggest shielding when below 50% HP.
private const val SHIELD_MIN_SAVED_NORMAL = 5      // Minimum flat HP saved to prefer shielding over attacking at high HP.
private const val SHIELD_MIN_SAVED_RATIO = 0.15    // Minimum fraction of max HP saved to prefer shielding at high HP.
private const val PREEMPTIVE_SHIELD_MAX_DIST = 6   // Max enemy distance (cells) to suggest a preemptive shield when idle.

/**
 * Pure tactical advisor inspired by `coach.ts`. Inspects enemy intents, HP/MP,
 * potions, cover positions, and lethal lines to highlight the smartest play.
 */
object TacticalCoach {

    /**
     * Evaluates the current player turn in priority order (finish lethal target,
     * survive incoming burst, stop healer, step to cover, shield, stun, attack,
     * restore MP, reposition, end turn) and returns the highest-value [CoachHint].
     */
    fun advise(state: CombatState, selectedTargetId: String?): CoachHint? {
        if (state.winner != null || !state.isPlayerTurn) return null
        val me = state.player
        val foes = state.aliveEnemies
        if (foes.isEmpty()) return null
        val target = foes.firstOrNull { it.id == selectedTargetId } ?: foes.first()
        val showIntro = state.round == 1 && me.ap == AP_PER_TURN
        val dist = distance(me.pos, target.pos)

        val intents = TacticalCombat.enemyIntents(state)
        val threats = intents.filter { it.targetId == me.id && (it.expectedDamage != null || it.kind == IntentKind.Control) }
        val incoming = threats.sumOf { it.expectedDamage ?: 0 }
        val topThreatId = threats.maxByOrNull { it.expectedDamage ?: 0 }?.actorId ?: target.id

        fun incomingIn(next: CombatState): Int =
            TacticalCombat.enemyIntents(next)
                .filter { it.targetId == me.id }
                .sumOf { it.expectedDamage ?: 0 }

        fun tryAction(action: CombatAction): CombatState? = try {
            TacticalCombat.performAction(state, action)
        } catch (_: CombatActionException) {
            null
        }

        val affordableSkills = me.skills.filter { it.mpCost <= me.mp && it.apCost <= me.ap }
        val attackSkills = affordableSkills.filter { it.effect == SkillEffect.Attack }
        val controlSkill = affordableSkills.firstOrNull { it.effect == SkillEffect.Control && dist <= it.range }
        val shieldSkill = affordableSkills.firstOrNull { it.effect == SkillEffect.Defense }
        val healSkill = affordableSkills.firstOrNull { it.effect == SkillEffect.Heal }

        fun weaponDmg(foe: Combatant): Int =
            if (me.ap >= me.weapon.apCost && distance(me.pos, foe.pos) <= me.weapon.range) {
                TacticalCombat.previewWeaponAttack(state, me, foe).damage
            } else {
                0
            }

        fun finisher(foe: Combatant): Pair<CoachPick, SkillId?>? {
            val d = distance(me.pos, foe.pos)
            val wDmg = weaponDmg(foe)
            fun weaponFollowup(remAp: Int): Int =
                if (me.weapon.apCost > 0 && d <= me.weapon.range) (remAp / me.weapon.apCost) * wDmg else 0

            val bestSkill = attackSkills
                .filter { d <= it.range }
                .map { it to TacticalCombat.previewSkillAttack(state, me, foe, it).damage }
                .maxByOrNull { it.second }
            if (bestSkill != null && bestSkill.second > 0 &&
                bestSkill.second + weaponFollowup(me.ap - bestSkill.first.apCost) >= foe.hp
            ) {
                return CoachPick.Skill(bestSkill.first.id) to bestSkill.first.id
            }
            if (wDmg > 0 && weaponFollowup(me.ap) >= foe.hp) {
                return CoachPick.Weapon to null
            }
            return null
        }

        fun bestDamageOn(foe: Combatant): Int {
            val d = distance(me.pos, foe.pos)
            val bestSkill = attackSkills
                .filter { d <= it.range }
                .maxOfOrNull { TacticalCombat.previewSkillAttack(state, me, foe, it).damage } ?: 0
            val wHits = if (me.weapon.apCost > 0 && d <= me.weapon.range) {
                (me.ap / me.weapon.apCost) * TacticalCombat.previewWeaponAttack(state, me, foe).damage
            } else {
                0
            }
            return max(bestSkill, wHits)
        }

        // 1. Finish an enemy if possible this turn (most dangerous first)
        val dangerOf = { id: String -> threats.filter { it.actorId == id }.sumOf { it.expectedDamage ?: 0 } }
        val kills = foes
            .mapNotNull { f -> finisher(f)?.let { f to it } }
            .sortedWith(
                compareByDescending<Pair<Combatant, Pair<CoachPick, SkillId?>>> { dangerOf(it.first.id) }
                    .thenByDescending { it.first.id == target.id },
            )
        kills.firstOrNull()?.let { (foe, pickPair) ->
            return if (foe.id == target.id) {
                CoachHint(
                    message = CoachMessage.FinishTarget(foe.id, foe.hp, pickPair.second),
                    pick = pickPair.first,
                    showIntro = showIntro,
                )
            } else {
                CoachHint(
                    message = CoachMessage.FocusFinish(foe.id, foe.hp, pickPair.second),
                    pick = pickPair.first,
                    focusEnemyId = foe.id,
                    showIntro = showIntro,
                )
            }
        }

        // 2. Emergency heal or HP potion when facing lethal damage or low HP
        val lethal = incoming >= me.hp
        val lowHpThreshold = (me.maxHp * if (lethal) HEAL_HP_RATIO_LETHAL else HEAL_HP_RATIO_NORMAL).roundToInt()
        if (me.hp < lowHpThreshold) {
            if (healSkill != null && me.hp < me.maxHp) {
                return CoachHint(
                    message = CoachMessage.DrinkHpPotion(incoming, me.hp, lethal),
                    pick = CoachPick.Skill(healSkill.id),
                    showIntro = showIntro,
                )
            }
            if (state.hpPotions > 0 && me.ap >= POTION_AP) {
                return CoachHint(
                    message = CoachMessage.DrinkHpPotion(incoming, me.hp, lethal),
                    pick = CoachPick.HpPotion,
                    showIntro = showIntro,
                )
            }
        }

        // 3. Focus enemy healer (Shroom) when it is about to mend
        val healer = foes.firstOrNull { f ->
            f.knack == EnemyKnack.Mend && f.id != target.id && bestDamageOn(f) > 0 &&
                intents.any { it.actorId == f.id && it.kind == IntentKind.Mend }
        }
        if (healer != null) {
            return CoachHint(
                message = CoachMessage.FocusHealer(healer.id),
                pick = CoachPick.Field(),
                focusEnemyId = healer.id,
                showIntro = showIntro,
            )
        }

        // 4. Step to cover / dodge out of heavy incoming damage
        val occupied = TacticalCombat.occupiedBy(state, me)
        if (incoming >= me.maxHp * DODGE_INCOMING_HP_RATIO && me.ap > 1) {
            var bestSpot: Pair<Pos, Int>? = null
            for (dx in -me.cellsPerAp..me.cellsPerAp) {
                for (dy in -me.cellsPerAp..me.cellsPerAp) {
                    if (dx == 0 && dy == 0) continue
                    val to = Pos(me.pos.x + dx, me.pos.y + dy)
                    if (!state.grid.inBounds(to) || state.grid.isCover(to)) continue
                    if (findPath(state.grid, me.pos, to, occupied, me.cellsPerAp) == null) continue
                    val next = tryAction(CombatAction.Move(to)) ?: continue
                    val after = incomingIn(next)
                    if (bestSpot == null || after < bestSpot.second ||
                        (after == bestSpot.second && distance(to, target.pos) < distance(bestSpot.first, target.pos))
                    ) {
                        bestSpot = to to after
                    }
                }
            }
            val minWorthSaving = max(DODGE_MIN_SAVED_HP, (me.maxHp * DODGE_MIN_SAVED_RATIO).roundToInt())
            if (bestSpot != null && incoming - bestSpot.second >= minWorthSaving) {
                return CoachHint(
                    message = CoachMessage.DodgeToCover(topThreatId, incoming, bestSpot.second),
                    pick = CoachPick.Field(to = bestSpot.first),
                    showIntro = showIntro,
                )
            }
        }

        // 5. Raise shield if it saves from death or saves more HP than an attack would deal
        if (shieldSkill != null && incoming > 0 && me.shield == null) {
            val shielded = tryAction(CombatAction.UseSkill(shieldSkill.id))
            val after = if (shielded != null) incomingIn(shielded) else incoming
            val saved = incoming - after
            val bestAttack = foes.maxOfOrNull { bestDamageOn(it) } ?: 0
            val survives = lethal && after < me.hp
            val lowHp = me.hp < me.maxHp * SHIELD_LOW_HP_RATIO
            val minNormalSave = max(SHIELD_MIN_SAVED_NORMAL, (me.maxHp * SHIELD_MIN_SAVED_RATIO).roundToInt())
            if (survives && saved >= SHIELD_MIN_SAVED_LETHAL ||
                (lowHp && saved >= SHIELD_MIN_SAVED_LOW_HP) ||
                (saved >= minNormalSave && saved > bestAttack)
            ) {
                return CoachHint(
                    message = CoachMessage.RaiseShield(topThreatId, incoming, saved, shieldSkill.id, survives),
                    pick = CoachPick.Skill(shieldSkill.id),
                    showIntro = showIntro,
                )
            }
        }

        // 6. Control skill (stun) on an unshielded, unstunned, non-steeled target
        if (controlSkill != null && target.stunned == 0 && target.steeled == 0 && target.shield == null) {
            return CoachHint(
                message = CoachMessage.UseControl(target.id, controlSkill.id),
                pick = CoachPick.Skill(controlSkill.id),
                showIntro = showIntro,
            )
        }

        // 7. Best offensive skill in range
        val readySkill = attackSkills
            .filter { dist <= it.range }
            .map { it to TacticalCombat.previewSkillAttack(state, me, target, it) }
            .maxByOrNull { it.second.damage }
        if (readySkill != null) {
            return CoachHint(
                message = CoachMessage.CastAttack(
                    targetId = target.id,
                    skillId = readySkill.first.id,
                    damage = readySkill.second.damage,
                    blocked = readySkill.second.blocked,
                ),
                pick = CoachPick.Skill(readySkill.first.id),
                showIntro = showIntro,
            )
        }

        // 8. Weapon attack if in range
        if (me.ap >= me.weapon.apCost && dist <= me.weapon.range) {
            return CoachHint(
                message = CoachMessage.StrikeWeapon(target.id),
                pick = CoachPick.Weapon,
                showIntro = showIntro,
            )
        }

        // 9. Drink MP potion if out of MP for all skills and we have one
        val minSkillMp = me.skills.minOfOrNull { it.mpCost } ?: Int.MAX_VALUE
        if (me.mp < minSkillMp && state.mpPotions > 0 && me.ap >= POTION_AP && me.mp < me.maxMp) {
            return CoachHint(
                message = CoachMessage.DrinkMpPotion(me.mp, me.maxMp),
                pick = CoachPick.MpPotion,
                showIntro = showIntro,
            )
        }

        // 10. Switch to another enemy already in range
        if (bestDamageOn(target) == 0) {
            val nearer = foes
                .filter { it.id != target.id }
                .map { it to bestDamageOn(it) }
                .maxByOrNull { it.second }
            if (nearer != null && nearer.second > 0) {
                return CoachHint(
                    message = CoachMessage.SwitchToReachable(target.id, nearer.first.id),
                    pick = CoachPick.Field(),
                    focusEnemyId = nearer.first.id,
                    showIntro = showIntro,
                )
            }
        }

        // 11. Step closer safely
        val step = stepToward(state.grid, me.pos, target.pos, me.cellsPerAp, occupied)
        if (step != null && me.ap > 0) {
            val moved = tryAction(CombatAction.Move(step))
            val exposed = if (moved != null) incomingIn(moved) else Int.MAX_VALUE
            val safe = exposed < me.hp || lethal
            if (safe) {
                return CoachHint(
                    message = CoachMessage.ApproachTarget(target.id, dist),
                    pick = CoachPick.Field(to = step),
                    showIntro = showIntro,
                )
            }
        }

        // 12. Preemptive shield if unable to hit and enemy is fairly close
        if (shieldSkill != null && me.shield == null && dist <= PREEMPTIVE_SHIELD_MAX_DIST) {
            return CoachHint(
                message = CoachMessage.PreemptiveShield(target.id, shieldSkill.id),
                pick = CoachPick.Skill(shieldSkill.id),
                showIntro = showIntro,
            )
        }

        return CoachHint(
            message = CoachMessage.EndTurnAdvice,
            pick = CoachPick.EndTurn,
            showIntro = showIntro,
        )
    }

    /** Returns true when [hint] currently highlights the UI control identified by [pick]. */
    fun isPicked(hint: CoachHint?, pick: CoachPick): Boolean {
        val p = hint?.pick ?: return false
        return p == pick
    }
}
