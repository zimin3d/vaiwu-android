package su.neit.vaiwu.combat

import su.neit.vaiwu.InventorySnapshot
import su.neit.vaiwu.PetSnapshot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Heroine build parameters decoupled from Android JNI snapshots so encounter
 * balance and unit tests can run cleanly on pure JVM.
 */
data class HeroBuild(
    val characterId: Int,
    val customName: String,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val mp: Int,
    val maxMp: Int,
    val strength: Int,
    val vitality: Int,
    val agility: Int,
    val magic: Int,
    val luck: Int,
    val weaponId: WeaponId,
    val armorBonus: Int,
    val hpPotions: Int,
    val mpPotions: Int,
)

object EncounterBuilder {

    const val DUNGEON_MIN_ROOMS = 3
    const val DUNGEON_MAX_ROOMS = 5
    const val REST_HP_PERCENT = 25
    const val REST_MP_PERCENT = 20

    fun heroBuildFromSnapshots(pet: PetSnapshot, inv: InventorySnapshot): HeroBuild {
        val equippedWeapon = inv.equipment.firstOrNull { it.slot == 0 && it.equipped }
        val weaponId = WeaponId.fromEquipId(equippedWeapon?.id ?: -1)
        val armorSlot = inv.equipment.firstOrNull { it.slot == 1 && it.equipped }
        val armorGearBonus = when (armorSlot?.id) {
            4 -> 2 // LeatherVest
            5 -> 4 // IronArmor
            9 -> 3 // ScaleMail
            10 -> 2 // SilkCloak
            else -> 0
        }
        return HeroBuild(
            characterId = pet.characterId.coerceIn(0, 7),
            customName = pet.petName,
            level = inv.level.coerceAtLeast(1),
            hp = inv.hp.coerceIn(1, inv.hpMax.coerceAtLeast(1)),
            maxHp = inv.hpMax.coerceAtLeast(1),
            mp = inv.mp.coerceIn(0, inv.mpMax.coerceAtLeast(0)),
            maxMp = inv.mpMax.coerceAtLeast(1),
            strength = inv.strength.coerceAtLeast(1),
            vitality = inv.vitality.coerceAtLeast(1),
            agility = inv.agility.coerceAtLeast(1),
            magic = inv.magic.coerceAtLeast(1),
            luck = inv.luck.coerceAtLeast(1),
            weaponId = weaponId,
            armorBonus = armorGearBonus,
            hpPotions = inv.potions.coerceAtLeast(0),
            mpPotions = inv.mpPotions.coerceAtLeast(0),
        )
    }

    fun buildHero(build: HeroBuild, startPos: Pos = Pos(1, 2)): Combatant {
        val weapon = buildWeapon(build)
        val skills = skillsForCharacter(build)
        val armor = build.armorBonus + build.vitality / 3
        val mpRegen = 2 + build.magic / 3
        return Combatant(
            id = PLAYER_ID,
            side = Side.Player,
            characterId = build.characterId,
            customName = build.customName,
            level = build.level,
            maxHp = build.maxHp,
            hp = build.hp.coerceIn(1, build.maxHp),
            maxMp = build.maxMp,
            mp = build.mp.coerceIn(0, build.maxMp),
            mpRegen = mpRegen,
            ap = AP_PER_TURN,
            cellsPerAp = CELLS_PER_AP,
            armor = armor,
            agility = build.agility,
            luck = build.luck,
            skills = skills,
            weapon = weapon,
            pos = startPos,
            aiRole = AiRole.Melee,
        )
    }

    private fun buildWeapon(build: HeroBuild): TacticalWeapon {
        val str = build.strength
        val agi = build.agility
        val mag = build.magic
        return when (build.weaponId) {
            WeaponId.WoodenSword -> TacticalWeapon(
                id = WeaponId.WoodenSword,
                minDamage = 5 + str,
                maxDamage = 8 + str,
                apCost = 1,
                range = 1,
            )
            WeaponId.SteelSword -> TacticalWeapon(
                id = WeaponId.SteelSword,
                minDamage = 7 + str,
                maxDamage = 11 + str,
                apCost = 1,
                range = 1,
            )
            WeaponId.MagicStaff -> TacticalWeapon(
                id = WeaponId.MagicStaff,
                minDamage = 4 + mag,
                maxDamage = 7 + mag,
                apCost = 1,
                range = 4,
            )
            WeaponId.WindDagger -> TacticalWeapon(
                id = WeaponId.WindDagger,
                minDamage = 5 + (str + agi) / 2,
                maxDamage = 8 + (str + agi) / 2,
                apCost = 1,
                range = 1,
                poisonChance = 0.30,
                poisonDamage = 2 + build.level / 2,
                critChance = 0.14,
            )
            WeaponId.MoonBlade -> TacticalWeapon(
                id = WeaponId.MoonBlade,
                minDamage = 7 + str + agi / 3,
                maxDamage = 11 + str + agi / 2,
                apCost = 1,
                range = 1,
                critChance = 0.15,
            )
            WeaponId.Fists -> {
                val base = max(str, mag / 2 + 1)
                TacticalWeapon(
                    id = WeaponId.Fists,
                    minDamage = 4 + base,
                    maxDamage = 6 + base,
                    apCost = 1,
                    range = 1,
                )
            }
        }
    }

    /** Returns the 3 unique tactical skills for each of the 8 Vaiwu heroines. */
    fun skillsForCharacter(build: HeroBuild): List<TacticalSkill> {
        val str = build.strength
        val vit = build.vitality
        val agi = build.agility
        val mag = build.magic
        val luck = build.luck
        val lvl = build.level

        return when (build.characterId) {
            // 0: Aoi (Light / Water)
            0 -> listOf(
                TacticalSkill(
                    id = SkillId.AoiFlash,
                    element = Element.Light,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 4,
                    power = 7 + mag * 2 + lvl,
                    penetration = 0.35,
                ),
                TacticalSkill(
                    id = SkillId.AoiIceBind,
                    element = Element.Water,
                    effect = SkillEffect.Control,
                    apCost = 2,
                    mpCost = 5,
                    range = 3,
                    power = 4 + mag + lvl / 2,
                    stunChance = 0.85,
                ),
                TacticalSkill(
                    id = SkillId.AoiMirrorShield,
                    element = Element.Light,
                    effect = SkillEffect.Defense,
                    apCost = 1,
                    mpCost = 4,
                    range = 0,
                    power = 10 + mag * 2 + vit + lvl,
                ),
            )
            // 1: Hina (Wind)
            1 -> listOf(
                TacticalSkill(
                    id = SkillId.HinaFlurry,
                    element = Element.Wind,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 1,
                    power = 4 + str + agi / 2 + lvl / 2,
                    hits = 2,
                ),
                TacticalSkill(
                    id = SkillId.HinaWindGust,
                    element = Element.Wind,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 3,
                    power = 6 + agi + mag + lvl,
                    pushCells = 1,
                ),
                TacticalSkill(
                    id = SkillId.HinaGaleBarrier,
                    element = Element.Wind,
                    effect = SkillEffect.Defense,
                    apCost = 1,
                    mpCost = 4,
                    range = 0,
                    power = 9 + agi * 2 + vit + lvl,
                ),
            )
            // 2: Rin (Fire)
            2 -> listOf(
                TacticalSkill(
                    id = SkillId.RinPreciseThrust,
                    element = Element.Fire,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 1,
                    power = 6 + str * 2 + lvl,
                    penetration = 0.50,
                    guaranteedCrit = true,
                ),
                TacticalSkill(
                    id = SkillId.RinFlameSlash,
                    element = Element.Fire,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 2,
                    power = 6 + str + mag + lvl,
                    burnAmount = 4 + (mag + lvl) / 2,
                ),
                TacticalSkill(
                    id = SkillId.RinFireShield,
                    element = Element.Fire,
                    effect = SkillEffect.Defense,
                    apCost = 1,
                    mpCost = 4,
                    range = 0,
                    power = 10 + vit * 2 + str + lvl,
                ),
            )
            // 3: Yuki (Water / Dark)
            3 -> listOf(
                TacticalSkill(
                    id = SkillId.YukiDrain,
                    element = Element.Dark,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 5,
                    range = 4,
                    power = 6 + mag * 2 + lvl,
                    lifestealRatio = 0.50,
                ),
                TacticalSkill(
                    id = SkillId.YukiFrostRay,
                    element = Element.Water,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 5,
                    power = 7 + mag * 2 + lvl,
                    penetration = 0.15,
                ),
                TacticalSkill(
                    id = SkillId.YukiIceShield,
                    element = Element.Water,
                    effect = SkillEffect.Defense,
                    apCost = 1,
                    mpCost = 4,
                    range = 0,
                    power = 10 + mag * 2 + vit + lvl,
                ),
            )
            // 4: Akane (Wind)
            4 -> listOf(
                TacticalSkill(
                    id = SkillId.AkaneLuckyStrike,
                    element = Element.Wind,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 2,
                    power = 6 + luck * 2 + agi / 2 + lvl,
                    stealGold = true,
                ),
                TacticalSkill(
                    id = SkillId.AkaneSmokeVeil,
                    element = Element.Wind,
                    effect = SkillEffect.Veil,
                    apCost = 1,
                    mpCost = 3,
                    range = 0,
                    power = 0,
                ),
                TacticalSkill(
                    id = SkillId.AkaneWindBarrier,
                    element = Element.Wind,
                    effect = SkillEffect.Defense,
                    apCost = 1,
                    mpCost = 4,
                    range = 0,
                    power = 9 + agi + luck + vit + lvl,
                ),
            )
            // 5: Uni (Light)
            5 -> listOf(
                TacticalSkill(
                    id = SkillId.UniHeal,
                    element = Element.Light,
                    effect = SkillEffect.Heal,
                    apCost = 1,
                    mpCost = 4,
                    range = 0,
                    power = 12 + mag * 2 + vit + lvl * 2,
                    cleanseDots = true,
                ),
                TacticalSkill(
                    id = SkillId.UniLightRay,
                    element = Element.Light,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 4,
                    power = 7 + mag * 2 + lvl,
                    bonusVsDark = 1.35,
                ),
                TacticalSkill(
                    id = SkillId.UniLightShield,
                    element = Element.Light,
                    effect = SkillEffect.Defense,
                    apCost = 1,
                    mpCost = 4,
                    range = 0,
                    power = 11 + vit * 2 + mag + lvl,
                ),
            )
            // 6: Kolobok (Earth)
            6 -> listOf(
                TacticalSkill(
                    id = SkillId.KolobokStoneRam,
                    element = Element.Earth,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 1,
                    power = 6 + vit * 2 + str + lvl,
                    stunChance = 0.55,
                ),
                TacticalSkill(
                    id = SkillId.KolobokEarthTremor,
                    element = Element.Earth,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 2,
                    power = 6 + vit + str + lvl,
                    ignoresCover = true,
                ),
                TacticalSkill(
                    id = SkillId.KolobokHardCrust,
                    element = Element.Earth,
                    effect = SkillEffect.Defense,
                    apCost = 1,
                    mpCost = 4,
                    range = 0,
                    power = 14 + vit * 2 + luck + lvl,
                    shieldDecay = 0.20,
                ),
            )
            // 7: Vesper (Dark)
            else -> listOf(
                TacticalSkill(
                    id = SkillId.VesperDarkBolt,
                    element = Element.Dark,
                    effect = SkillEffect.Attack,
                    apCost = 1,
                    mpCost = 4,
                    range = 4,
                    power = 7 + mag * 2 + lvl,
                    burnAmount = 3 + mag / 3,
                    poisonTurns = 2,
                    poisonDamage = 2 + mag / 3,
                ),
                TacticalSkill(
                    id = SkillId.VesperShadowStep,
                    element = Element.Dark,
                    effect = SkillEffect.Teleport,
                    apCost = 1,
                    mpCost = 3,
                    range = 4,
                    power = 0,
                ),
                TacticalSkill(
                    id = SkillId.VesperShadowShroud,
                    element = Element.Dark,
                    effect = SkillEffect.Defense,
                    apCost = 1,
                    mpCost = 4,
                    range = 0,
                    power = 10 + mag * 2 + agi + lvl,
                ),
            )
        }
    }

    fun buildMonster(
        id: String,
        kind: MonsterKind,
        level: Int,
        pos: Pos,
        depthPct: Int = 100,
        boss: Boolean = false,
    ): Combatant {
        val lvl = level.coerceAtLeast(1)
        val hpPct = when (kind) {
            MonsterKind.Slime -> 100
            MonsterKind.Bat -> 80
            MonsterKind.Shroom -> 115
            MonsterKind.Fox -> 95
            MonsterKind.Ghost -> 85
            MonsterKind.Golem -> 145
        }
        val atkPct = when (kind) {
            MonsterKind.Slime -> 100
            MonsterKind.Bat -> 115
            MonsterKind.Shroom -> 90
            MonsterKind.Fox -> 110
            MonsterKind.Ghost -> 125
            MonsterKind.Golem -> 105
        }
        val rewardPct = when (kind) {
            MonsterKind.Slime, MonsterKind.Bat, MonsterKind.Shroom -> 100
            MonsterKind.Fox -> 115
            MonsterKind.Ghost -> 125
            MonsterKind.Golem -> 140
        }

        var hpMax = ((16 + lvl * 6) * hpPct / 100) * depthPct / 100
        var atk = ((4 + lvl * 2) * atkPct / 100) * depthPct / 100
        var xp = (10 + lvl * 5) * rewardPct / 100
        var gold = (3 + lvl * 2) * rewardPct / 100
        if (boss) {
            hpMax = (hpMax * 1.65).roundToInt()
            atk = (atk * 1.25).roundToInt()
            xp *= 2
            gold *= 2
        }

        val minHit = max(2, atk - 1)
        val maxHit = atk + 2
        val poisonDmg = 2 + lvl / 2

        return when (kind) {
            MonsterKind.Slime -> Combatant(
                id = id,
                side = Side.Enemy,
                monsterKind = kind,
                isBoss = boss,
                level = lvl,
                maxHp = hpMax,
                hp = hpMax,
                maxMp = 10,
                mp = 10,
                mpRegen = 2,
                armor = lvl / 3,
                weapon = TacticalWeapon(WeaponId.Fists, minHit, maxHit, apCost = 2, range = 1, poisonChance = 0.35, poisonDamage = poisonDmg),
                skills = listOf(
                    TacticalSkill(
                        id = SkillId.MonsterSlimeSpit,
                        element = Element.Water,
                        effect = SkillEffect.Attack,
                        apCost = 2,
                        mpCost = 4,
                        range = 2,
                        power = atk,
                        poisonTurns = 3,
                        poisonDamage = poisonDmg,
                    ),
                ),
                pos = pos,
                aiRole = AiRole.Melee,
                xpReward = xp,
                goldReward = gold,
            )
            MonsterKind.Bat -> Combatant(
                id = id,
                side = Side.Enemy,
                monsterKind = kind,
                isBoss = boss,
                level = lvl,
                maxHp = hpMax,
                hp = hpMax,
                maxMp = 8,
                mp = 8,
                mpRegen = 2,
                cellsPerAp = 3,
                agility = 4 + lvl,
                weapon = TacticalWeapon(WeaponId.Fists, minHit, maxHit, apCost = 2, range = 1),
                skills = listOf(
                    TacticalSkill(
                        id = SkillId.MonsterBatScreech,
                        element = Element.Wind,
                        effect = SkillEffect.Attack,
                        apCost = 2,
                        mpCost = 4,
                        range = 1,
                        power = atk,
                        stunChance = 0.35,
                    ),
                ),
                pos = pos,
                aiRole = AiRole.Melee,
                xpReward = xp,
                goldReward = gold,
            )
            MonsterKind.Shroom -> Combatant(
                id = id,
                side = Side.Enemy,
                monsterKind = kind,
                isBoss = boss,
                level = lvl,
                maxHp = hpMax,
                hp = hpMax,
                maxMp = 16,
                mp = 16,
                mpRegen = 3,
                armor = 1 + lvl / 3,
                weapon = TacticalWeapon(WeaponId.Fists, max(2, minHit - 1), atk, apCost = 2, range = 1),
                skills = listOf(
                    TacticalSkill(
                        id = SkillId.MonsterShroomSpores,
                        element = Element.Earth,
                        effect = SkillEffect.Attack,
                        apCost = 2,
                        mpCost = 4,
                        range = 4,
                        power = atk,
                        poisonTurns = 3,
                        poisonDamage = poisonDmg,
                    ),
                ),
                pos = pos,
                aiRole = AiRole.Caster,
                knack = EnemyKnack.Mend,
                xpReward = xp,
                goldReward = gold,
            )
            MonsterKind.Fox -> Combatant(
                id = id,
                side = Side.Enemy,
                monsterKind = kind,
                isBoss = boss,
                level = lvl,
                maxHp = hpMax,
                hp = hpMax,
                maxMp = 12,
                mp = 12,
                mpRegen = 2,
                agility = 3 + lvl,
                weapon = TacticalWeapon(WeaponId.Fists, minHit, maxHit, apCost = 2, range = 4),
                skills = listOf(
                    TacticalSkill(
                        id = SkillId.MonsterFoxShot,
                        element = Element.Fire,
                        effect = SkillEffect.Attack,
                        apCost = 2,
                        mpCost = 4,
                        range = 4,
                        power = atk + 1,
                    ),
                ),
                pos = pos,
                aiRole = AiRole.Ranged,
                knack = EnemyKnack.Command,
                stealGoldOnHit = true,
                xpReward = xp,
                goldReward = gold,
            )
            MonsterKind.Ghost -> Combatant(
                id = id,
                side = Side.Enemy,
                monsterKind = kind,
                isBoss = boss,
                level = lvl,
                maxHp = hpMax,
                hp = hpMax,
                maxMp = 18,
                mp = 18,
                mpRegen = 3,
                weapon = TacticalWeapon(WeaponId.Fists, minHit, maxHit, apCost = 2, range = 3),
                skills = listOf(
                    TacticalSkill(
                        id = SkillId.MonsterGhostBolt,
                        element = Element.Dark,
                        effect = SkillEffect.Attack,
                        apCost = 2,
                        mpCost = 4,
                        range = 4,
                        power = atk + 1,
                        penetration = 0.35,
                    ),
                    TacticalSkill(
                        id = SkillId.MonsterGhostShield,
                        element = Element.Dark,
                        effect = SkillEffect.Defense,
                        apCost = 1,
                        mpCost = 4,
                        range = 0,
                        power = 8 + lvl * 2,
                    ),
                ),
                pos = pos,
                aiRole = AiRole.Caster,
                xpReward = xp,
                goldReward = gold,
            )
            MonsterKind.Golem -> Combatant(
                id = id,
                side = Side.Enemy,
                monsterKind = kind,
                isBoss = boss,
                level = lvl,
                maxHp = hpMax,
                hp = hpMax,
                maxMp = 12,
                mp = 12,
                mpRegen = 2,
                cellsPerAp = 1,
                armor = 2 + lvl / 2,
                poisonImmune = true,
                weapon = TacticalWeapon(WeaponId.Fists, minHit + 1, maxHit + 2, apCost = 2, range = 1),
                skills = listOf(
                    TacticalSkill(
                        id = SkillId.MonsterGolemSlam,
                        element = Element.Earth,
                        effect = SkillEffect.Attack,
                        apCost = 2,
                        mpCost = 4,
                        range = 1,
                        power = atk + 2,
                        stunChance = 0.25,
                    ),
                    TacticalSkill(
                        id = SkillId.MonsterGolemShield,
                        element = Element.Earth,
                        effect = SkillEffect.Defense,
                        apCost = 1,
                        mpCost = 4,
                        range = 0,
                        power = 10 + lvl * 2,
                        shieldDecay = 0.20,
                    ),
                ),
                pos = pos,
                aiRole = AiRole.Melee,
                xpReward = xp,
                goldReward = gold,
            )
        }
    }

    private fun unlockedMonsterCount(petLevel: Int): Int {
        var kinds = 3
        if (petLevel >= 3) kinds++
        if (petLevel >= 5) kinds++
        if (petLevel >= 7) kinds++
        return kinds
    }

    fun startSession(
        hero: HeroBuild,
        dungeon: Boolean,
        seed: Long,
    ): TacticalBattleSession {
        val rng0 = SeededRng.fromSeed(seed)
        val (rooms, rng1) = if (dungeon) {
            rng0.nextInt(DUNGEON_MIN_ROOMS, DUNGEON_MAX_ROOMS)
        } else {
            1 to rng0
        }
        val state = buildRoomCombat(
            hero = hero,
            dungeon = dungeon,
            room = 1,
            rooms = rooms,
            seed = rng1.seed,
        )
        return TacticalBattleSession(
            dungeon = dungeon,
            room = 1,
            rooms = rooms,
            isBossRoom = dungeon && rooms == 1,
            state = state,
        )
    }

    fun buildRoomCombat(
        hero: HeroBuild,
        dungeon: Boolean,
        room: Int,
        rooms: Int,
        seed: Long,
    ): CombatState {
        var rng = SeededRng.fromSeed(seed)
        val isBoss = dungeon && room >= rooms
        val playerPos = Pos(1, 2)
        val enemySlots = listOf(Pos(7, 2), Pos(7, 0), Pos(7, 4))
        val foeCount = when {
            isBoss -> 2
            dungeon && room >= 2 -> 2
            !dungeon && hero.level >= 3 -> 2
            else -> 1
        }
        val kinds = unlockedMonsterCount(hero.level)
        val depthPct = if (dungeon) 100 + (room - 1) * 10 else 100

        val enemies = mutableListOf<Combatant>()
        for (i in 0 until foeCount) {
            val (kindIdx, r1) = rng.nextInt(0, kinds - 1)
            val (lvlDrop, r2) = r1.nextInt(0, 1)
            rng = r2
            val isThisBoss = isBoss && i == 0
            val baseLevel = max(1, hero.level - lvlDrop)
            val enemyLevel = if (isThisBoss) hero.level + 1 else if (isBoss) max(1, hero.level - 1) else baseLevel
            val pos = enemySlots[i % enemySlots.size]
            enemies += buildMonster(
                id = "foe_$i",
                kind = MonsterKind.fromNative(kindIdx),
                level = enemyLevel,
                pos = pos,
                depthPct = if (isBoss && !isThisBoss) 85 else depthPct,
                boss = isThisBoss,
            )
        }

        val theme = when {
            isBoss -> ArenaTheme.BossHall
            dungeon -> ArenaTheme.Dungeon
            hero.level >= 3 -> ArenaTheme.Forest
            else -> ArenaTheme.Meadow
        }
        val reserved = (listOf(playerPos) + enemies.map { it.pos }).toSet()
        val (grid, nextRng) = generateArenaGrid(theme, rng, reserved)
        val player = buildHero(hero, playerPos)
        return TacticalCombat.createCombat(
            combatants = listOf(player) + enemies,
            grid = grid,
            seed = nextRng.seed,
            hpPotions = hero.hpPotions,
            mpPotions = hero.mpPotions,
        )
    }

    fun computeDungeonChestGold(rooms: Int, seed: Long): Int {
        val (bonus, _) = SeededRng.fromSeed(seed).nextInt(0, 9)
        return 5 * rooms + bonus
    }
}
