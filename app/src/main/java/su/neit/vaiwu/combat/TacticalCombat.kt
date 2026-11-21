package su.neit.vaiwu.combat

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_LOG_ENTRIES = 200
private const val CRIT_MULTIPLIER = 1.5

private data class AiStep(
    val actorId: String,
    val act: AiAct,
    val targetId: String? = null,
    val skillId: SkillId? = null,
    val effect: SkillEffect? = null,
    val melee: Boolean = false,
    val expectedDamage: Int? = null,
)

private enum class AiAct {
    Move,
    Cover,
    Shield,
    Skill,
    Weapon,
    Mend,
    Command,
}

private val INTENT_PRIORITY = listOf(
    IntentKind.Command,
    IntentKind.Mend,
    IntentKind.Attack,
    IntentKind.Control,
    IntentKind.Shield,
    IntentKind.Cover,
    IntentKind.Approach,
    IntentKind.Stunned,
    IntentKind.Idle,
)

/**
 * Pure, immutable tactical combat engine. Every public function takes a [CombatState]
 * and returns a brand-new [CombatState] without mutating the input.
 */
object TacticalCombat {

    fun createCombat(
        combatants: List<Combatant>,
        grid: TacticalGrid,
        seed: Long,
        hpPotions: Int = 0,
        mpPotions: Int = 0,
    ): CombatState {
        require(combatants.isNotEmpty()) { "Combat requires at least one combatant" }
        val builder = MutableCombat(
            grid = grid,
            combatants = combatants.toMutableList(),
            activeIndex = 0,
            round = 1,
            events = mutableListOf(),
            eventSeq = 0,
            log = mutableListOf(CombatLogEntry.BattleStarted),
            rng = SeededRng.fromSeed(seed),
            winner = null,
            hpPotions = hpPotions.coerceAtLeast(0),
            mpPotions = mpPotions.coerceAtLeast(0),
            goldDelta = 0,
            xpEarned = 0,
        )
        builder.startTurn(0)
        if (builder.winner == null && (!builder.combatants[0].isAlive || builder.combatants[0].ap <= 0)) {
            builder.advance()
        }
        return builder.snapshot()
    }

    fun performAction(state: CombatState, action: CombatAction): CombatState {
        if (state.winner != null) throw CombatActionException(ActionFailReason.BattleOver)
        val draft = MutableCombat.from(state, clearEvents = true)
        draft.applyAction(action)
        return draft.snapshot()
    }

    /** Runs all enemy turns until the active turn returns to the player or the battle ends. */
    fun runEnemyTurns(state: CombatState): CombatState {
        if (state.winner != null || state.activeCombatant.side == Side.Player) return state
        val draft = MutableCombat.from(state, clearEvents = false)
        draft.runAiLoop(trace = null)
        return draft.snapshot()
    }

    /** Convenience helper: performs a player action, then automatically runs enemy turns if the turn passed. */
    fun performAndResolve(state: CombatState, action: CombatAction): CombatState {
        val afterPlayer = performAction(state, action)
        return if (afterPlayer.winner == null && afterPlayer.activeCombatant.side == Side.Enemy) {
            runEnemyTurns(afterPlayer)
        } else {
            afterPlayer
        }
    }

    /**
     * Deterministic dry-run of enemy turns on a copy of [state] to reveal what each enemy
     * intends to do if the player ends their turn now.
     */
    fun enemyIntents(state: CombatState): List<EnemyIntent> {
        if (state.winner != null || state.activeCombatant.side != Side.Player) return emptyList()
        val trace = mutableListOf<AiStep>()
        val draft = MutableCombat.from(state, clearEvents = true)
        draft.advance()
        draft.runAiLoop(trace = trace)

        fun kindOf(step: AiStep): IntentKind = when (step.act) {
            AiAct.Skill -> if (step.effect == SkillEffect.Control) IntentKind.Control else IntentKind.Attack
            AiAct.Weapon -> IntentKind.Attack
            AiAct.Move -> IntentKind.Approach
            AiAct.Cover -> IntentKind.Cover
            AiAct.Shield -> IntentKind.Shield
            AiAct.Mend -> IntentKind.Mend
            AiAct.Command -> IntentKind.Command
        }

        return state.combatants
            .filter { it.side == Side.Enemy && it.isAlive }
            .map { enemy ->
                val steps = trace.filter { it.actorId == enemy.id }
                if (steps.isEmpty()) {
                    EnemyIntent(
                        actorId = enemy.id,
                        kind = if (enemy.stunned > 0) IntentKind.Stunned else IntentKind.Idle,
                        moves = false,
                    )
                } else {
                    val main = steps.minBy { INTENT_PRIORITY.indexOf(kindOf(it)) }
                    val kind = kindOf(main)
                    val damageSteps = steps.filter { it.expectedDamage != null && it.targetId == main.targetId }
                    val totalDamage = if (damageSteps.isNotEmpty()) {
                        damageSteps.sumOf { it.expectedDamage ?: 0 }
                    } else {
                        null
                    }
                    EnemyIntent(
                        actorId = enemy.id,
                        kind = kind,
                        targetId = main.targetId,
                        skillId = main.skillId,
                        weaponAttack = main.act == AiAct.Weapon,
                        melee = main.melee,
                        expectedDamage = totalDamage,
                        moves = kind != IntentKind.Approach &&
                            kind != IntentKind.Cover &&
                            steps.any { it.act == AiAct.Move || it.act == AiAct.Cover },
                    )
                }
            }
    }

    /** Target is covered only against ranged attacks (distance > 1). */
    fun isCovered(grid: TacticalGrid, actor: Combatant, target: Combatant): Boolean =
        actor.id != target.id && distance(actor.pos, target.pos) > 1 && hasCover(grid, actor.pos, target.pos)

    fun occupiedBy(state: CombatState, actor: Combatant): Set<Pos> =
        state.combatants.asSequence()
            .filter { it.id != actor.id && it.isAlive }
            .map { it.pos }
            .toSet()

    /** Returns the shortest valid move path if [actor] has enough AP to walk to [to], else null. */
    fun reachablePath(state: CombatState, actor: Combatant, to: Pos): List<Pos>? {
        if (!state.grid.inBounds(to) || state.grid.isCover(to)) return null
        val occupied = occupiedBy(state, actor)
        if (to in occupied) return null
        val maxCells = actor.ap * actor.cellsPerAp.coerceAtLeast(1)
        if (maxCells <= 0) return null
        val path = findPath(state.grid, actor.pos, to, occupied, maxCells) ?: return null
        return if (path.isNotEmpty() && pathCost(path, actor.cellsPerAp) <= actor.ap) path else null
    }

    /** True if [to] is a valid destination for a teleport skill (like Shadow Step). */
    fun canTeleportTo(state: CombatState, actor: Combatant, skill: TacticalSkill, to: Pos): Boolean {
        if (skill.effect != SkillEffect.Teleport) return false
        if (!state.grid.inBounds(to) || state.grid.isCover(to)) return false
        if (to == actor.pos || to in occupiedBy(state, actor)) return false
        return distance(actor.pos, to) <= skill.range
    }

    fun previewWeaponAttack(
        state: CombatState,
        actor: Combatant,
        target: Combatant,
    ): AttackPreview {
        val weapon = actor.weapon
        val inRange = distance(actor.pos, target.pos) <= weapon.range
        val covered = weapon.range > 1 && isCovered(state.grid, actor, target)
        val avg = (weapon.minDamage + weapon.maxDamage) / 2.0
        var power = if (covered) avg * COVER_WEAPON else avg
        return computePreview(actor, target, power, Element.None, 0.0, inRange, covered, hits = 1)
    }

    fun previewSkillAttack(
        state: CombatState,
        actor: Combatant,
        target: Combatant,
        skill: TacticalSkill,
    ): AttackPreview {
        val inRange = distance(actor.pos, target.pos) <= skill.range
        val covered = !skill.ignoresCover && skill.range > 1 && isCovered(state.grid, actor, target)
        var power = skill.power.toDouble()
        if (skill.guaranteedCrit) power *= CRIT_MULTIPLIER
        if (skill.bonusVsDark > 1.0 && isDarkAligned(target)) power *= skill.bonusVsDark
        if (covered) power *= COVER_SPELL
        return computePreview(actor, target, power, skill.element, skill.penetration, inRange, covered, hits = skill.hits)
    }

    fun forecast(state: CombatState, actor: Combatant, target: Combatant): List<ForecastLine> {
        val lines = mutableListOf<ForecastLine>()
        for (skill in actor.skills) {
            if (skill.effect != SkillEffect.Attack) continue
            val p = previewSkillAttack(state, actor, target, skill)
            lines += ForecastLine(
                source = ForecastSource.Skill(skill.id),
                damage = p.damage,
                inRange = p.inRange,
                covered = p.covered,
                blocked = p.blocked,
                kills = p.kills,
            )
        }
        val wp = previewWeaponAttack(state, actor, target)
        lines += ForecastLine(
            source = ForecastSource.Weapon(actor.weapon.id),
            damage = wp.damage,
            inRange = wp.inRange,
            covered = wp.covered,
            blocked = wp.blocked,
            kills = wp.kills,
        )
        return lines.sortedWith(
            compareByDescending<ForecastLine> { it.inRange && !it.blocked }
                .thenByDescending { it.damage },
        )
    }

    private fun computePreview(
        actor: Combatant,
        target: Combatant,
        basePower: Double,
        element: Element,
        penetration: Double,
        inRange: Boolean,
        covered: Boolean,
        hits: Int,
    ): AttackPreview {
        var remainingShield = target.shield?.value ?: 0.0
        val shieldElement = target.shield?.element ?: Element.None
        var totalDamage = 0
        var allBlocked = remainingShield > 0.0

        repeat(hits.coerceAtLeast(1)) {
            var power = basePower
            if (target.marked > 0 && actor.side != target.side) power *= COMMAND_BONUS
            if (target.veiled > 0 && distance(actor.pos, target.pos) > 1) power *= VEIL_FACTOR

            val effectiveShield = if (remainingShield > 0.0) {
                remainingShield * defenseFactor(shieldElement, element) * (1.0 - penetration)
            } else {
                0.0
            }

            if (effectiveShield > 0.0 && power <= effectiveShield) {
                val factor = defenseFactor(shieldElement, element).coerceAtLeast(0.1)
                remainingShield = (remainingShield - power / factor).coerceAtLeast(0.0)
            } else {
                allBlocked = false
                val afterShield = (power - effectiveShield).coerceAtLeast(0.0)
                remainingShield = 0.0
                val dealt = applyArmor(afterShield.roundToInt(), target.armor)
                totalDamage += dealt
            }
        }
        return AttackPreview(
            inRange = inRange,
            covered = covered,
            damage = totalDamage,
            blocked = allBlocked && totalDamage == 0,
            kills = inRange && !allBlocked && totalDamage >= target.hp,
        )
    }
}

private fun applyArmor(rawDamage: Int, armor: Int): Int {
    if (rawDamage <= 0) return 0
    return max(1, rawDamage - armor.coerceAtLeast(0))
}

private fun isDarkAligned(c: Combatant): Boolean =
    c.monsterKind == MonsterKind.Ghost ||
        c.monsterKind == MonsterKind.Bat ||
        c.skills.any { it.element == Element.Dark }

private class MutableCombat(
    val grid: TacticalGrid,
    val combatants: MutableList<Combatant>,
    var activeIndex: Int,
    var round: Int,
    val events: MutableList<CombatEvent>,
    var eventSeq: Int,
    val log: MutableList<CombatLogEntry>,
    var rng: SeededRng,
    var winner: CombatWinner?,
    var hpPotions: Int,
    var mpPotions: Int,
    var goldDelta: Int,
    var xpEarned: Int,
) {
    companion object {
        fun from(state: CombatState, clearEvents: Boolean): MutableCombat = MutableCombat(
            grid = state.grid,
            combatants = state.combatants.toMutableList(),
            activeIndex = state.activeIndex,
            round = state.round,
            events = if (clearEvents) mutableListOf() else state.events.toMutableList(),
            eventSeq = state.eventSeq,
            log = state.log.toMutableList(),
            rng = state.rng,
            winner = state.winner,
            hpPotions = state.hpPotions,
            mpPotions = state.mpPotions,
            goldDelta = state.goldDelta,
            xpEarned = state.xpEarned,
        )
    }

    fun snapshot(): CombatState = CombatState(
        grid = grid,
        combatants = combatants.toList(),
        activeIndex = activeIndex,
        round = round,
        events = events.toList(),
        eventSeq = eventSeq,
        log = log.toList(),
        rng = rng,
        winner = winner,
        hpPotions = hpPotions,
        mpPotions = mpPotions,
        goldDelta = goldDelta,
        xpEarned = xpEarned,
    )

    private fun nextDouble(): Double {
        val (value, next) = rng.nextDouble()
        rng = next
        return value
    }

    private fun nextInt(minInclusive: Int, maxInclusive: Int): Int {
        val (value, next) = rng.nextInt(minInclusive, maxInclusive)
        rng = next
        return value
    }

    private fun nextEventId(): Int = ++eventSeq

    private fun emit(event: CombatEvent) {
        events += event
    }

    private fun appendLog(entry: CombatLogEntry) {
        log += entry
        if (log.size > MAX_LOG_ENTRIES) {
            log.subList(0, log.size - MAX_LOG_ENTRIES).clear()
        }
    }

    private fun indexOf(id: String): Int = combatants.indexOfFirst { it.id == id }

    private fun requireTargetIndex(id: String): Int {
        val idx = indexOf(id)
        if (idx < 0 || !combatants[idx].isAlive) {
            throw CombatActionException(ActionFailReason.InvalidTarget)
        }
        return idx
    }

    private fun update(index: Int, transform: (Combatant) -> Combatant): Combatant {
        val updated = transform(combatants[index])
        combatants[index] = updated
        return updated
    }

    private fun occupiedFor(actorId: String): Set<Pos> =
        combatants.asSequence()
            .filter { it.id != actorId && it.isAlive }
            .map { it.pos }
            .toSet()

    private fun spendAp(actorIndex: Int, cost: Int) {
        val actor = combatants[actorIndex]
        if (actor.ap < cost) throw CombatActionException(ActionFailReason.NotEnoughAp)
        combatants[actorIndex] = actor.copy(ap = actor.ap - cost)
    }

    fun applyAction(action: CombatAction) {
        val actorIdx = activeIndex
        when (action) {
            is CombatAction.Move -> move(actorIdx, action.to)
            is CombatAction.WeaponAttack -> weaponAttack(actorIdx, requireTargetIndex(action.targetId))
            is CombatAction.UseSkill -> useSkill(actorIdx, action.skillId, action.targetId, action.targetPos)
            CombatAction.UseHpPotion -> useHpPotion(actorIdx)
            CombatAction.UseMpPotion -> useMpPotion(actorIdx)
            CombatAction.Flee -> attemptFlee(actorIdx)
            CombatAction.EndTurn -> advance()
        }
    }

    private fun move(actorIdx: Int, to: Pos) {
        val actor = combatants[actorIdx]
        if (!grid.inBounds(to)) throw CombatActionException(ActionFailReason.OutOfBounds)
        if (grid.isCover(to)) throw CombatActionException(ActionFailReason.TileBlocked)
        val occupied = occupiedFor(actor.id)
        if (to in occupied) throw CombatActionException(ActionFailReason.TileBlocked)
        val path = findPath(grid, actor.pos, to, occupied)
            ?: throw CombatActionException(ActionFailReason.NoPath)
        val cost = pathCost(path, actor.cellsPerAp)
        if (cost == 0) return
        spendAp(actorIdx, cost)
        val from = actor.pos
        update(actorIdx) { it.copy(pos = to) }
        emit(CombatEvent.Move(nextEventId(), actor.id, from, to, path, teleport = false))
        appendLog(CombatLogEntry.Moved(actor.id, teleport = false))
    }

    private fun weaponAttack(actorIdx: Int, targetIdx: Int) {
        val actor = combatants[actorIdx]
        val target = combatants[targetIdx]
        val weapon = actor.weapon
        if (actorIdx != targetIdx && distance(actor.pos, target.pos) > weapon.range) {
            throw CombatActionException(ActionFailReason.OutOfRange)
        }
        spendAp(actorIdx, weapon.apCost)
        val covered = weapon.range > 1 && TacticalCombat.isCovered(grid, actor, target)
        var raw = nextInt(weapon.minDamage, weapon.maxDamage).toDouble()
        val critChance = (weapon.critChance + actor.luck * 0.015).coerceIn(0.05, 0.45)
        val crit = nextDouble() < critChance
        if (crit) raw *= CRIT_MULTIPLIER
        if (covered) raw *= COVER_WEAPON

        emit(CombatEvent.WeaponStrike(nextEventId(), actor.id, target.id, weapon.id, weapon.range <= 1))
        appendLog(CombatLogEntry.WeaponHit(actor.id, target.id, weapon.id, covered))
        val dealt = resolveHit(actorIdx, targetIdx, raw, Element.None, 0.0, crit)

        if (dealt > 0 && combatants[targetIdx].isAlive) {
            if (weapon.poisonChance > 0.0 && !combatants[targetIdx].poisonImmune && nextDouble() < weapon.poisonChance) {
                applyPoison(targetIdx, turns = 3, damage = weapon.poisonDamage)
            }
            if (actor.stealGoldOnHit && target.side == Side.Player && nextDouble() < 0.25) {
                val stolen = 2 + actor.level
                goldDelta -= stolen
                emit(CombatEvent.StatusApplied(nextEventId(), target.id, StatusTag.StolenGold, stolen))
                appendLog(CombatLogEntry.GoldChanged(target.id, -stolen))
            }
        }
        checkEnd(targetIdx)
    }

    private fun useSkill(actorIdx: Int, skillId: SkillId, targetId: String?, targetPos: Pos?) {
        val actor = combatants[actorIdx]
        val skill = actor.skills.firstOrNull { it.id == skillId }
            ?: throw CombatActionException(ActionFailReason.SkillNotFound)
        if (actor.mp < skill.mpCost) throw CombatActionException(ActionFailReason.NotEnoughMp)

        when (skill.effect) {
            SkillEffect.Defense -> {
                spendAp(actorIdx, skill.apCost)
                update(actorIdx) {
                    it.copy(
                        mp = it.mp - skill.mpCost,
                        shield = ShieldState(skill.power.toDouble(), skill.element, skill.shieldDecay),
                    )
                }
                emit(CombatEvent.ShieldUp(nextEventId(), actor.id, skill.element, skill.power, skill.id))
                appendLog(CombatLogEntry.ShieldRaised(actor.id, skill.id, skill.power))
            }
            SkillEffect.Heal -> {
                spendAp(actorIdx, skill.apCost)
                val healed = min(actor.maxHp - actor.hp, skill.power.coerceAtLeast(1))
                val hadDots = actor.burn > 0 || actor.poisonTurns > 0
                update(actorIdx) {
                    it.copy(
                        mp = it.mp - skill.mpCost,
                        hp = it.hp + healed,
                        burn = if (skill.cleanseDots) 0 else it.burn,
                        poisonTurns = if (skill.cleanseDots) 0 else it.poisonTurns,
                        poisonDamage = if (skill.cleanseDots) 0 else it.poisonDamage,
                    )
                }
                emit(CombatEvent.Heal(nextEventId(), actor.id, actor.id, healed))
                appendLog(CombatLogEntry.Healed(actor.id, actor.id, healed))
                if (skill.cleanseDots && hadDots) {
                    emit(CombatEvent.StatusApplied(nextEventId(), actor.id, StatusTag.Cleanse))
                    appendLog(CombatLogEntry.StatusChanged(actor.id, StatusTag.Cleanse))
                }
            }
            SkillEffect.Veil -> {
                if (actor.veiled > 0) throw CombatActionException(ActionFailReason.AlreadyVeiled)
                spendAp(actorIdx, skill.apCost)
                update(actorIdx) { it.copy(mp = it.mp - skill.mpCost, veiled = 1) }
                emit(CombatEvent.StatusApplied(nextEventId(), actor.id, StatusTag.Veiled))
                appendLog(CombatLogEntry.StatusChanged(actor.id, StatusTag.Veiled))
            }
            SkillEffect.Teleport -> {
                val dest = targetPos ?: throw CombatActionException(ActionFailReason.OutOfBounds)
                if (!grid.inBounds(dest)) throw CombatActionException(ActionFailReason.OutOfBounds)
                if (grid.isCover(dest) || dest in occupiedFor(actor.id)) {
                    throw CombatActionException(ActionFailReason.TileBlocked)
                }
                if (dest == actor.pos || distance(actor.pos, dest) > skill.range) {
                    throw CombatActionException(ActionFailReason.OutOfRange)
                }
                spendAp(actorIdx, skill.apCost)
                val from = actor.pos
                update(actorIdx) { it.copy(mp = it.mp - skill.mpCost, pos = dest) }
                emit(CombatEvent.Move(nextEventId(), actor.id, from, dest, listOf(dest), teleport = true))
                appendLog(CombatLogEntry.Moved(actor.id, teleport = true))
            }
            SkillEffect.Control -> {
                val targetIdx = requireTargetIndex(targetId ?: throw CombatActionException(ActionFailReason.InvalidTarget))
                val target = combatants[targetIdx]
                if (distance(actor.pos, target.pos) > skill.range) {
                    throw CombatActionException(ActionFailReason.OutOfRange)
                }
                spendAp(actorIdx, skill.apCost)
                update(actorIdx) { it.copy(mp = it.mp - skill.mpCost) }
                val covered = !skill.ignoresCover && skill.range > 1 && TacticalCombat.isCovered(grid, actor, target)
                emit(CombatEvent.SkillCast(nextEventId(), actor.id, target.id, skill.id, skill.element, skill.effect, skill.power))
                appendLog(CombatLogEntry.SkillUsed(actor.id, target.id, skill.id, covered))
                val power = if (covered) skill.power * COVER_SPELL else skill.power.toDouble()
                val dealt = if (power > 0) resolveHit(actorIdx, targetIdx, power, skill.element, skill.penetration, crit = false) else 0
                if (combatants[targetIdx].isAlive && (dealt > 0 || skill.power == 0)) {
                    tryApplyStun(targetIdx, skill.stunChance.coerceAtLeast(0.8))
                }
                checkEnd(targetIdx)
            }
            SkillEffect.Attack -> {
                val targetIdx = requireTargetIndex(targetId ?: throw CombatActionException(ActionFailReason.InvalidTarget))
                val target = combatants[targetIdx]
                if (distance(actor.pos, target.pos) > skill.range) {
                    throw CombatActionException(ActionFailReason.OutOfRange)
                }
                spendAp(actorIdx, skill.apCost)
                update(actorIdx) { it.copy(mp = it.mp - skill.mpCost) }
                val covered = !skill.ignoresCover && skill.range > 1 && TacticalCombat.isCovered(grid, actor, target)
                emit(CombatEvent.SkillCast(nextEventId(), actor.id, target.id, skill.id, skill.element, skill.effect, skill.power))
                appendLog(CombatLogEntry.SkillUsed(actor.id, target.id, skill.id, covered))

                var totalDealt = 0
                repeat(skill.hits.coerceAtLeast(1)) {
                    if (!combatants[targetIdx].isAlive) return@repeat
                    val variance = 0.9 + nextDouble() * 0.2
                    var power = skill.power * variance
                    val crit = skill.guaranteedCrit || (nextDouble() < (0.06 + actor.luck * 0.015).coerceAtMost(0.4))
                    if (crit) power *= CRIT_MULTIPLIER
                    if (skill.bonusVsDark > 1.0 && isDarkAligned(combatants[targetIdx])) {
                        power *= skill.bonusVsDark
                    }
                    if (covered) power *= COVER_SPELL
                    totalDealt += resolveHit(actorIdx, targetIdx, power, skill.element, skill.penetration, crit)
                }

                if (totalDealt > 0) {
                    if (skill.lifestealRatio > 0.0 && combatants[actorIdx].isAlive) {
                        val cur = combatants[actorIdx]
                        val heal = min(cur.maxHp - cur.hp, max(1, (totalDealt * skill.lifestealRatio).roundToInt()))
                        if (heal > 0) {
                            update(actorIdx) { it.copy(hp = it.hp + heal) }
                            emit(CombatEvent.Heal(nextEventId(), cur.id, cur.id, heal))
                            appendLog(CombatLogEntry.Healed(cur.id, cur.id, heal))
                        }
                    }
                    if (skill.stealGold && actor.side == Side.Player && nextDouble() < 0.45) {
                        val snagged = 2 + actor.level
                        goldDelta += snagged
                        emit(CombatEvent.StatusApplied(nextEventId(), actor.id, StatusTag.StolenGold, snagged))
                        appendLog(CombatLogEntry.GoldChanged(actor.id, snagged))
                    }
                    if (combatants[targetIdx].isAlive) {
                        if (skill.burnAmount > 0) {
                            update(targetIdx) { it.copy(burn = it.burn + skill.burnAmount) }
                            emit(CombatEvent.StatusApplied(nextEventId(), target.id, StatusTag.Burn, skill.burnAmount))
                            appendLog(CombatLogEntry.StatusChanged(target.id, StatusTag.Burn, skill.burnAmount))
                        }
                        if (skill.poisonTurns > 0 && !combatants[targetIdx].poisonImmune) {
                            applyPoison(targetIdx, skill.poisonTurns, skill.poisonDamage.coerceAtLeast(2))
                        }
                        if (skill.stunChance > 0.0) {
                            tryApplyStun(targetIdx, skill.stunChance)
                        }
                        if (skill.pushCells > 0) {
                            pushTarget(actorIdx, targetIdx, skill.pushCells)
                        }
                    }
                }
                checkEnd(targetIdx)
            }
        }
    }

    private fun pushTarget(actorIdx: Int, targetIdx: Int, cells: Int) {
        val actor = combatants[actorIdx]
        val target = combatants[targetIdx]
        val dx = (target.pos.x - actor.pos.x).coerceIn(-1, 1)
        val dy = (target.pos.y - actor.pos.y).coerceIn(-1, 1)
        if (dx == 0 && dy == 0) return
        val occupied = occupiedFor(target.id)
        var dest = target.pos
        repeat(cells) {
            val next = Pos(dest.x + dx, dest.y + dy)
            if (grid.inBounds(next) && !grid.isCover(next) && next !in occupied) {
                dest = next
            }
        }
        if (dest != target.pos) {
            val from = target.pos
            update(targetIdx) { it.copy(pos = dest) }
            emit(CombatEvent.Move(nextEventId(), target.id, from, dest, listOf(dest), teleport = false))
            emit(CombatEvent.StatusApplied(nextEventId(), target.id, StatusTag.Pushed))
            appendLog(CombatLogEntry.StatusChanged(target.id, StatusTag.Pushed))
        }
    }

    private fun applyPoison(targetIdx: Int, turns: Int, damage: Int) {
        val t = combatants[targetIdx]
        if (t.poisonImmune) return
        update(targetIdx) {
            it.copy(
                poisonTurns = max(it.poisonTurns, turns),
                poisonDamage = max(it.poisonDamage, damage),
            )
        }
        emit(CombatEvent.StatusApplied(nextEventId(), t.id, StatusTag.Poison, turns))
        appendLog(CombatLogEntry.StatusChanged(t.id, StatusTag.Poison, turns))
    }

    private fun tryApplyStun(targetIdx: Int, chance: Double) {
        val t = combatants[targetIdx]
        if (t.steeled > 0) {
            emit(CombatEvent.StatusApplied(nextEventId(), t.id, StatusTag.Steeled))
            appendLog(CombatLogEntry.StatusChanged(t.id, StatusTag.Steeled))
            return
        }
        if (nextDouble() < chance) {
            update(targetIdx) { it.copy(stunned = max(it.stunned, 1)) }
            emit(CombatEvent.StatusApplied(nextEventId(), t.id, StatusTag.Stunned))
            appendLog(CombatLogEntry.StatusChanged(t.id, StatusTag.Stunned))
        }
    }

    private fun absorbByShield(
        targetIdx: Int,
        power: Double,
        element: Element,
        penetration: Double,
    ): Double {
        val target = combatants[targetIdx]
        val shield = target.shield ?: return power
        val factor = defenseFactor(shield.element, element).coerceAtLeast(0.1)
        val effective = shield.value * factor * (1.0 - penetration)
        return if (power > effective) {
            update(targetIdx) { it.copy(shield = null) }
            appendLog(CombatLogEntry.ShieldBroken(target.id))
            power - effective
        } else {
            val nextVal = shield.value - power / factor
            update(targetIdx) {
                it.copy(shield = if (nextVal < 1.0) null else shield.copy(value = nextVal))
            }
            emit(CombatEvent.Blocked(nextEventId(), target.id))
            appendLog(CombatLogEntry.ShieldHeld(target.id))
            0.0
        }
    }

    private fun resolveHit(
        actorIdx: Int,
        targetIdx: Int,
        rawPower: Double,
        element: Element,
        penetration: Double,
        crit: Boolean,
    ): Int {
        val actor = combatants[actorIdx]
        val target = combatants[targetIdx]
        var power = rawPower
        if (target.marked > 0 && actor.side != target.side) power *= COMMAND_BONUS
        if (target.veiled > 0 && distance(actor.pos, target.pos) > 1) power *= VEIL_FACTOR

        val remaining = absorbByShield(targetIdx, power, element, penetration)
        val dealt = applyArmor(remaining.roundToInt(), combatants[targetIdx].armor)
        if (dealt > 0) {
            update(targetIdx) { it.copy(hp = max(0, it.hp - dealt)) }
            emit(CombatEvent.Damage(nextEventId(), target.id, dealt, DamageKind.Hit, crit))
            appendLog(CombatLogEntry.DamageTaken(target.id, dealt, DamageKind.Hit, crit))
        }
        return dealt
    }

    private fun useHpPotion(actorIdx: Int) {
        val actor = combatants[actorIdx]
        if (actor.side != Side.Player || hpPotions <= 0) {
            throw CombatActionException(ActionFailReason.NoPotion)
        }
        if (actor.hp >= actor.maxHp) throw CombatActionException(ActionFailReason.AlreadyFull)
        spendAp(actorIdx, POTION_AP)
        hpPotions--
        val heal = min(actor.maxHp - actor.hp, max(1, (actor.maxHp * POTION_HEAL_RATIO).roundToInt()))
        update(actorIdx) { it.copy(hp = it.hp + heal) }
        emit(CombatEvent.Heal(nextEventId(), actor.id, actor.id, heal))
        appendLog(CombatLogEntry.Healed(actor.id, actor.id, heal))
    }

    private fun useMpPotion(actorIdx: Int) {
        val actor = combatants[actorIdx]
        if (actor.side != Side.Player || mpPotions <= 0) {
            throw CombatActionException(ActionFailReason.NoPotion)
        }
        if (actor.mp >= actor.maxMp) throw CombatActionException(ActionFailReason.AlreadyFull)
        spendAp(actorIdx, POTION_AP)
        mpPotions--
        val gain = min(actor.maxMp - actor.mp, max(1, (actor.maxMp * POTION_MP_RATIO).roundToInt()))
        update(actorIdx) { it.copy(mp = it.mp + gain) }
        emit(CombatEvent.MpRestore(nextEventId(), actor.id, gain))
        appendLog(CombatLogEntry.MpRestored(actor.id, gain))
    }

    private fun attemptFlee(actorIdx: Int) {
        val actor = combatants[actorIdx]
        spendAp(actorIdx, FLEE_AP)
        val atEdge = actor.pos.x == 0
        val chance = if (atEdge) 1.0 else (0.40 + actor.agility * 0.03).coerceIn(0.35, 0.90)
        val escaped = nextDouble() < chance
        emit(CombatEvent.FleeAttempt(nextEventId(), actor.id, escaped))
        appendLog(CombatLogEntry.FleeResult(escaped))
        if (escaped) {
            winner = CombatWinner.Escaped
            appendLog(CombatLogEntry.BattleEnded(CombatWinner.Escaped))
        }
    }

    private fun mendAlly(actorIdx: Int, allyIdx: Int) {
        spendAp(actorIdx, MEND_AP)
        val actor = combatants[actorIdx]
        val ally = combatants[allyIdx]
        val healBase = 5 + actor.level * 2
        val healed = min(ally.maxHp - ally.hp, nextInt(healBase, healBase + 3))
        if (healed > 0) {
            update(allyIdx) { it.copy(hp = it.hp + healed) }
        }
        emit(CombatEvent.Heal(nextEventId(), actor.id, ally.id, healed))
        appendLog(CombatLogEntry.Healed(actor.id, ally.id, healed))
    }

    private fun commandPack(actorIdx: Int, targetIdx: Int) {
        spendAp(actorIdx, COMMAND_AP)
        val actor = combatants[actorIdx]
        val target = combatants[targetIdx]
        update(targetIdx) { it.copy(marked = 1) }
        emit(CombatEvent.CommandMark(nextEventId(), actor.id, target.id))
        appendLog(CombatLogEntry.Commanded(actor.id, target.id))
    }

    fun startTurn(idx: Int) {
        var c = combatants[idx]
        if (!c.isAlive) return

        // Poison tick
        if (c.poisonTurns > 0) {
            val dmg = c.poisonDamage.coerceAtLeast(1)
            c = update(idx) {
                val nextTurns = it.poisonTurns - 1
                it.copy(
                    hp = max(0, it.hp - dmg),
                    poisonTurns = nextTurns,
                    poisonDamage = if (nextTurns <= 0) 0 else it.poisonDamage,
                )
            }
            emit(CombatEvent.Damage(nextEventId(), c.id, dmg, DamageKind.Poison))
            appendLog(CombatLogEntry.DamageTaken(c.id, dmg, DamageKind.Poison, crit = false))
            checkEnd(idx)
            if (winner != null || !combatants[idx].isAlive) return
        }

        // Burn tick (halves each turn)
        c = combatants[idx]
        if (c.burn > 0) {
            val dmg = c.burn
            c = update(idx) {
                it.copy(
                    hp = max(0, it.hp - dmg),
                    burn = it.burn / 2,
                )
            }
            emit(CombatEvent.Damage(nextEventId(), c.id, dmg, DamageKind.Burn))
            appendLog(CombatLogEntry.DamageTaken(c.id, dmg, DamageKind.Burn, crit = false))
            checkEnd(idx)
            if (winner != null || !combatants[idx].isAlive) return
        }

        // Shield decay, passive MP inflow, AP refill, status countdowns
        c = update(idx) { cur ->
            val nextShield = cur.shield?.let { sh ->
                val v = sh.value * (1.0 - sh.decay)
                if (v < 1.0) null else sh.copy(value = v)
            }
            val nextMp = if (cur.maxMp > 0) min(cur.maxMp, cur.mp + cur.mpRegen) else cur.mp
            val nextSteeled = (cur.steeled - 1).coerceAtLeast(0)
            val nextMarked = (cur.marked - 1).coerceAtLeast(0)
            if (cur.stunned > 0) {
                val remStun = cur.stunned - 1
                cur.copy(
                    shield = nextShield,
                    mp = nextMp,
                    ap = 0,
                    stunned = remStun,
                    steeled = if (remStun == 0) STEELED_TURNS else nextSteeled,
                    veiled = 0,
                    marked = nextMarked,
                )
            } else {
                cur.copy(
                    shield = nextShield,
                    mp = nextMp,
                    ap = AP_PER_TURN,
                    steeled = nextSteeled,
                    veiled = 0,
                    marked = nextMarked,
                )
            }
        }
        if (c.ap == 0) {
            appendLog(CombatLogEntry.StunSkippedTurn(c.id))
        }
    }

    fun advance() {
        if (winner != null) return
        val n = combatants.size
        for (step in 0 until n * 2) {
            activeIndex = (activeIndex + 1) % n
            if (activeIndex == 0) round++
            val next = combatants[activeIndex]
            if (!next.isAlive) continue
            startTurn(activeIndex)
            if (winner != null) return
            if (combatants[activeIndex].isAlive && combatants[activeIndex].ap > 0) return
        }
    }

    private fun checkEnd(changedIdx: Int) {
        val changed = combatants[changedIdx]
        if (!changed.isAlive && events.none { it is CombatEvent.Down && it.targetId == changed.id }) {
            emit(CombatEvent.Down(nextEventId(), changed.id))
            appendLog(CombatLogEntry.Defeated(changed.id))
            if (changed.side == Side.Enemy) {
                xpEarned += changed.xpReward
                goldDelta += changed.goldReward
            }
        }
        val enemiesAlive = combatants.any { it.side == Side.Enemy && it.isAlive }
        val playerAlive = combatants.any { it.id == PLAYER_ID && it.isAlive }
        val nextWinner = when {
            !enemiesAlive -> CombatWinner.Player
            !playerAlive -> CombatWinner.Enemy
            else -> null
        }
        if (nextWinner != null && winner == null) {
            winner = nextWinner
            appendLog(CombatLogEntry.BattleEnded(nextWinner))
        }
    }

    fun runAiLoop(trace: MutableList<AiStep>?) {
        var guard = 0
        while (winner == null && combatants[activeIndex].side == Side.Enemy && guard++ < 40) {
            enemyTurn(activeIndex, trace)
        }
    }

    private fun coverStep(actor: Combatant, target: Combatant, desiredRange: Int): Pos? {
        if (hasCover(grid, target.pos, actor.pos)) return null
        val occupied = occupiedFor(actor.id)
        val maxCells = actor.cellsPerAp.coerceAtLeast(1)
        var best: Pos? = null
        var bestScore = Int.MAX_VALUE
        val currentDist = distance(actor.pos, target.pos)
        for (dx in -maxCells..maxCells) {
            for (dy in -maxCells..maxCells) {
                if (dx == 0 && dy == 0) continue
                val p = Pos(actor.pos.x + dx, actor.pos.y + dy)
                if (!grid.inBounds(p) || grid.isCover(p)) continue
                if (findPath(grid, actor.pos, p, occupied, maxCells) == null) continue
                val d = distance(p, target.pos)
                if (d > desiredRange || d < currentDist || !hasCover(grid, target.pos, p)) continue
                val score = distance(actor.pos, p) * 2 - d
                if (score < bestScore) {
                    best = p
                    bestScore = score
                }
            }
        }
        return best
    }

    private fun enemyTurn(actorIdx: Int, trace: MutableList<AiStep>?) {
        var tookCover = false
        var usedKnack = false
        for (iter in 0 until 10) {
            if (winner != null) break
            val actor = combatants[actorIdx]
            if (!actor.isAlive || actor.ap <= 0) break

            val targetIdx = combatants.indices
                .filter { combatants[it].side != actor.side && combatants[it].isAlive }
                .minByOrNull { combatants[it].hp } ?: break
            val target = combatants[targetIdx]
            val dist = distance(actor.pos, target.pos)

            val castable = actor.skills.filter { s ->
                s.mpCost <= actor.mp && s.apCost <= actor.ap &&
                    (s.effect == SkillEffect.Defense || s.effect == SkillEffect.Heal || s.range >= dist)
            }
            val shieldSkill = castable.firstOrNull { it.effect == SkillEffect.Defense }
            val offensiveSkill = castable
                .filter { it.effect == SkillEffect.Attack || it.effect == SkillEffect.Control }
                .maxByOrNull { it.power }
            val canWeapon = actor.ap >= actor.weapon.apCost && actor.weapon.range >= dist
            val wantedRange = max(
                actor.weapon.range,
                actor.skills.filter { it.effect == SkillEffect.Attack || it.effect == SkillEffect.Control }
                    .maxOfOrNull { it.range } ?: 0,
            )
            val attackAp = offensiveSkill?.apCost ?: if (canWeapon) actor.weapon.apCost else Int.MAX_VALUE

            // Knack 1: Shroom mends wounded ally within 4 cells
            if (!usedKnack && actor.knack == EnemyKnack.Mend && actor.ap >= MEND_AP) {
                val hurtIdx = combatants.indices
                    .filter { i ->
                        val c = combatants[i]
                        c.side == actor.side && c.isAlive &&
                            c.hp < c.maxHp * MEND_THRESHOLD &&
                            distance(actor.pos, c.pos) <= MEND_RANGE
                    }
                    .minByOrNull { combatants[it].hp.toDouble() / combatants[it].maxHp.coerceAtLeast(1) }
                if (hurtIdx != null) {
                    usedKnack = true
                    trace?.add(AiStep(actor.id, AiAct.Mend, targetId = combatants[hurtIdx].id))
                    mendAlly(actorIdx, hurtIdx)
                    continue
                }
            }

            // Knack 2: Fox commands pack (marks target if at least one ally is alive)
            if (!usedKnack && actor.knack == EnemyKnack.Command && actor.ap >= COMMAND_AP && target.marked == 0) {
                val hasPack = combatants.any { it.side == actor.side && it.id != actor.id && it.isAlive }
                if (hasPack) {
                    usedKnack = true
                    trace?.add(AiStep(actor.id, AiAct.Command, targetId = target.id))
                    commandPack(actorIdx, targetIdx)
                    continue
                }
            }

            // Ranged/Caster seeks cover before firing if it still leaves AP to attack
            val seeksCover = actor.aiRole == AiRole.Ranged || actor.aiRole == AiRole.Caster
            if (!tookCover && seeksCover && (offensiveSkill != null || canWeapon) && actor.ap - 1 >= attackAp) {
                tookCover = true
                val range = offensiveSkill?.range ?: actor.weapon.range
                val step = coverStep(actor, target, range)
                if (step != null) {
                    trace?.add(AiStep(actor.id, AiAct.Cover))
                    move(actorIdx, step)
                    continue
                }
            }

            val currentSnap = snapshot()
            if (actor.shield == null && shieldSkill != null && nextDouble() < 0.45) {
                trace?.add(AiStep(actor.id, AiAct.Shield, skillId = shieldSkill.id))
                useSkill(actorIdx, shieldSkill.id, null, null)
            } else if (offensiveSkill != null) {
                val expected = if (offensiveSkill.effect == SkillEffect.Attack) {
                    TacticalCombat.previewSkillAttack(currentSnap, actor, target, offensiveSkill).damage
                } else {
                    null
                }
                trace?.add(
                    AiStep(
                        actorId = actor.id,
                        act = AiAct.Skill,
                        targetId = target.id,
                        skillId = offensiveSkill.id,
                        effect = offensiveSkill.effect,
                        melee = offensiveSkill.range <= 1,
                        expectedDamage = expected,
                    ),
                )
                useSkill(actorIdx, offensiveSkill.id, target.id, null)
            } else if (canWeapon) {
                val expected = TacticalCombat.previewWeaponAttack(currentSnap, actor, target).damage
                trace?.add(
                    AiStep(
                        actorId = actor.id,
                        act = AiAct.Weapon,
                        targetId = target.id,
                        melee = actor.weapon.range <= 1,
                        expectedDamage = expected,
                    ),
                )
                weaponAttack(actorIdx, targetIdx)
            } else if (dist > wantedRange && wantedRange > 0 && actor.ap >= 1) {
                val step = stepToward(grid, actor.pos, target.pos, actor.cellsPerAp, occupiedFor(actor.id))
                    ?: break
                trace?.add(AiStep(actor.id, AiAct.Move))
                move(actorIdx, step)
            } else {
                break
            }
        }
        advance()
    }
}
