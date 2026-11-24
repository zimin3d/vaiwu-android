package su.neit.vaiwu.combat

import android.content.Context
import su.neit.vaiwu.R

/**
 * Maps pure-Kotlin combat identifiers, log entries, playback captions, and
 * tactical coach hints to localized strings in `res/values/strings.xml`.
 * Keeping all Russian text in XML keeps `SourceGuardTest` green.
 */
object TacticalStrings {

    fun skillNameRes(id: SkillId): Int = when (id) {
        SkillId.AoiFlash -> R.string.skill_aoi_flash
        SkillId.AoiIceBind -> R.string.skill_aoi_ice_bind
        SkillId.AoiMirrorShield -> R.string.skill_aoi_mirror_shield
        SkillId.HinaFlurry -> R.string.skill_hina_flurry
        SkillId.HinaWindGust -> R.string.skill_hina_wind_gust
        SkillId.HinaGaleBarrier -> R.string.skill_hina_gale_barrier
        SkillId.RinPreciseThrust -> R.string.skill_rin_precise_thrust
        SkillId.RinFlameSlash -> R.string.skill_rin_flame_slash
        SkillId.RinFireShield -> R.string.skill_rin_fire_shield
        SkillId.YukiDrain -> R.string.skill_yuki_drain
        SkillId.YukiFrostRay -> R.string.skill_yuki_frost_ray
        SkillId.YukiIceShield -> R.string.skill_yuki_ice_shield
        SkillId.AkaneLuckyStrike -> R.string.skill_akane_lucky_strike
        SkillId.AkaneSmokeVeil -> R.string.skill_akane_smoke_veil
        SkillId.AkaneWindBarrier -> R.string.skill_akane_wind_barrier
        SkillId.UniHeal -> R.string.skill_uni_heal
        SkillId.UniLightRay -> R.string.skill_uni_light_ray
        SkillId.UniLightShield -> R.string.skill_uni_light_shield
        SkillId.KolobokStoneRam -> R.string.skill_kolobok_stone_ram
        SkillId.KolobokEarthTremor -> R.string.skill_kolobok_earth_tremor
        SkillId.KolobokHardCrust -> R.string.skill_kolobok_hard_crust
        SkillId.VesperDarkBolt -> R.string.skill_vesper_dark_bolt
        SkillId.VesperShadowStep -> R.string.skill_vesper_shadow_step
        SkillId.VesperShadowShroud -> R.string.skill_vesper_shadow_shroud
        SkillId.MonsterSlimeSpit -> R.string.skill_monster_slime_spit
        SkillId.MonsterBatScreech -> R.string.skill_monster_bat_screech
        SkillId.MonsterShroomSpores -> R.string.skill_monster_shroom_spores
        SkillId.MonsterFoxShot -> R.string.skill_monster_fox_shot
        SkillId.MonsterGhostBolt -> R.string.skill_monster_ghost_bolt
        SkillId.MonsterGhostShield -> R.string.skill_monster_ghost_shield
        SkillId.MonsterGolemSlam -> R.string.skill_monster_golem_slam
        SkillId.MonsterGolemShield -> R.string.skill_monster_golem_shield
        SkillId.MonsterSalamanderEmber -> R.string.skill_monster_salamander_ember
        SkillId.MonsterMothPrism -> R.string.skill_monster_moth_prism
        SkillId.MonsterMothBlink -> R.string.skill_monster_moth_blink
        SkillId.MonsterMothVeil -> R.string.skill_monster_moth_veil
        SkillId.MonsterMimicBite -> R.string.skill_monster_mimic_bite
        SkillId.MonsterAutomatonGale -> R.string.skill_monster_automaton_gale
        SkillId.MonsterAutomatonAegis -> R.string.skill_monster_automaton_aegis
    }

    fun weaponNameRes(id: WeaponId): Int = when (id) {
        WeaponId.Fists -> R.string.weapon_fists
        WeaponId.WoodenSword -> R.string.weapon_wooden_sword
        WeaponId.SteelSword -> R.string.weapon_steel_sword
        WeaponId.MagicStaff -> R.string.weapon_magic_staff
        WeaponId.WindDagger -> R.string.weapon_wind_dagger
        WeaponId.MoonBlade -> R.string.weapon_moon_blade
    }

    fun monsterNameRes(kind: MonsterKind): Int = when (kind) {
        MonsterKind.Slime -> R.string.monster_slime
        MonsterKind.Bat -> R.string.monster_bat
        MonsterKind.Shroom -> R.string.monster_shroom
        MonsterKind.Fox -> R.string.monster_fox
        MonsterKind.Ghost -> R.string.monster_ghost
        MonsterKind.Golem -> R.string.monster_golem
        MonsterKind.Salamander -> R.string.monster_salamander
        MonsterKind.Moth -> R.string.monster_moth
        MonsterKind.Mimic -> R.string.monster_mimic
        MonsterKind.Automaton -> R.string.monster_automaton
    }

    fun elementNameRes(element: Element): Int = when (element) {
        Element.None -> R.string.element_none
        Element.Fire -> R.string.element_fire
        Element.Water -> R.string.element_water
        Element.Wind -> R.string.element_wind
        Element.Earth -> R.string.element_earth
        Element.Light -> R.string.element_light
        Element.Dark -> R.string.element_dark
    }

    fun combatantName(context: Context, combatant: Combatant): String {
        if (combatant.side == Side.Player) {
            if (combatant.customName.isNotBlank()) return combatant.customName
            val names = context.resources.getStringArray(R.array.character_names)
            return names.getOrElse(combatant.characterId) { names.first() }
        }
        val base = context.getString(monsterNameRes(combatant.monsterKind ?: MonsterKind.Slime))
        return if (combatant.isBoss) {
            context.getString(R.string.tactical_boss_name, base)
        } else {
            base
        }
    }

    fun nameById(context: Context, state: CombatState, id: String?): String {
        if (id == null) return ""
        val c = state.combatants.firstOrNull { it.id == id } ?: return ""
        return combatantName(context, c)
    }

    fun formatLogEntry(context: Context, state: CombatState, entry: CombatLogEntry): String = when (entry) {
        CombatLogEntry.BattleStarted -> context.getString(R.string.tactical_log_started)
        is CombatLogEntry.Moved -> context.getString(
            if (entry.teleport) R.string.tactical_log_teleported else R.string.tactical_log_moved,
            nameById(context, state, entry.actorId),
        )
        is CombatLogEntry.WeaponHit -> context.getString(
            if (entry.covered) R.string.tactical_log_weapon_covered else R.string.tactical_log_weapon,
            nameById(context, state, entry.actorId),
            context.getString(weaponNameRes(entry.weaponId)),
            nameById(context, state, entry.targetId),
        )
        is CombatLogEntry.SkillUsed -> context.getString(
            if (entry.covered) R.string.tactical_log_skill_covered else R.string.tactical_log_skill,
            nameById(context, state, entry.actorId),
            context.getString(skillNameRes(entry.skillId)),
            nameById(context, state, entry.targetId),
        )
        is CombatLogEntry.ShieldRaised -> context.getString(
            R.string.tactical_log_shield,
            nameById(context, state, entry.actorId),
            context.getString(skillNameRes(entry.skillId)),
            entry.value,
        )
        is CombatLogEntry.ShieldBroken -> context.getString(
            R.string.tactical_log_shield_broken,
            nameById(context, state, entry.targetId),
        )
        is CombatLogEntry.ShieldHeld -> context.getString(
            R.string.tactical_log_shield_held,
            nameById(context, state, entry.targetId),
        )
        is CombatLogEntry.DamageTaken -> when (entry.kind) {
            DamageKind.Burn -> context.getString(
                R.string.tactical_log_burn_tick,
                nameById(context, state, entry.targetId),
                entry.amount,
            )
            DamageKind.Poison -> context.getString(
                R.string.tactical_log_poison_tick,
                nameById(context, state, entry.targetId),
                entry.amount,
            )
            DamageKind.Hit -> context.getString(
                if (entry.crit) R.string.tactical_log_damage_crit else R.string.tactical_log_damage,
                nameById(context, state, entry.targetId),
                entry.amount,
            )
        }
        is CombatLogEntry.Healed -> context.getString(
            R.string.tactical_log_healed,
            nameById(context, state, entry.actorId),
            nameById(context, state, entry.targetId),
            entry.amount,
        )
        is CombatLogEntry.MpRestored -> context.getString(
            R.string.tactical_log_mp_restored,
            nameById(context, state, entry.targetId),
            entry.amount,
        )
        is CombatLogEntry.StatusChanged -> formatStatusLog(context, state, entry)
        is CombatLogEntry.StunSkippedTurn -> context.getString(
            R.string.tactical_log_stun_skip,
            nameById(context, state, entry.actorId),
        )
        is CombatLogEntry.Commanded -> context.getString(
            R.string.tactical_log_commanded,
            nameById(context, state, entry.actorId),
            nameById(context, state, entry.targetId),
        )
        is CombatLogEntry.GoldChanged -> context.getString(
            if (entry.delta >= 0) R.string.tactical_log_gold_gained else R.string.tactical_log_gold_lost,
            nameById(context, state, entry.actorId),
            kotlin.math.abs(entry.delta),
        )
        is CombatLogEntry.Defeated -> context.getString(
            R.string.tactical_log_defeated,
            nameById(context, state, entry.targetId),
        )
        is CombatLogEntry.FleeResult -> context.getString(
            if (entry.success) R.string.tactical_log_flee_ok else R.string.tactical_log_flee_fail,
        )
        is CombatLogEntry.BattleEnded -> context.getString(
            when (entry.winner) {
                CombatWinner.Player -> R.string.battle_victory
                CombatWinner.Enemy -> R.string.battle_fainted
                CombatWinner.Escaped -> R.string.tactical_log_flee_ok
            },
        )
    }

    private fun formatStatusLog(context: Context, state: CombatState, entry: CombatLogEntry.StatusChanged): String {
        val target = nameById(context, state, entry.targetId)
        return when (entry.tag) {
            StatusTag.Burn -> context.getString(R.string.tactical_status_burn, target, entry.amount)
            StatusTag.Poison -> context.getString(R.string.tactical_status_poison, target)
            StatusTag.Stunned -> context.getString(R.string.tactical_status_stunned, target)
            StatusTag.Steeled -> context.getString(R.string.tactical_status_steeled, target)
            StatusTag.Veiled -> context.getString(R.string.tactical_status_veiled, target)
            StatusTag.Marked -> context.getString(R.string.tactical_status_marked, target)
            StatusTag.Cleanse -> context.getString(R.string.tactical_status_cleanse, target)
            StatusTag.Pushed -> context.getString(R.string.tactical_status_pushed, target)
            StatusTag.StolenGold -> context.getString(R.string.tactical_log_gold_gained, target, entry.amount)
        }
    }

    fun formatCaption(context: Context, state: CombatState, event: CombatEvent?): String? = when (event) {
        null -> null
        is CombatEvent.Move -> context.getString(
            if (event.teleport) R.string.tactical_caption_teleport else R.string.tactical_caption_move,
            nameById(context, state, event.actorId),
        )
        is CombatEvent.SkillCast -> context.getString(
            R.string.tactical_caption_skill,
            nameById(context, state, event.actorId),
            context.getString(skillNameRes(event.skillId)),
        )
        is CombatEvent.WeaponStrike -> context.getString(
            R.string.tactical_caption_weapon,
            nameById(context, state, event.actorId),
            context.getString(weaponNameRes(event.weaponId)),
        )
        is CombatEvent.ShieldUp -> context.getString(
            R.string.tactical_caption_shield,
            nameById(context, state, event.actorId),
            context.getString(skillNameRes(event.skillId)),
        )
        is CombatEvent.Heal -> context.getString(
            R.string.tactical_caption_heal,
            nameById(context, state, event.actorId),
            event.amount,
        )
        is CombatEvent.MpRestore -> context.getString(
            R.string.tactical_caption_mp,
            nameById(context, state, event.targetId),
            event.amount,
        )
        is CombatEvent.CommandMark -> context.getString(
            R.string.tactical_caption_command,
            nameById(context, state, event.actorId),
        )
        is CombatEvent.FleeAttempt -> context.getString(
            if (event.success) R.string.tactical_log_flee_ok else R.string.tactical_log_flee_fail,
        )
        else -> null
    }

    fun formatCoachHint(context: Context, state: CombatState, hint: CoachHint): String {
        val body = when (val msg = hint.message) {
            is CoachMessage.FinishTarget -> {
                val weaponOrSkill = msg.skillId?.let { context.getString(skillNameRes(it)) }
                    ?: context.getString(weaponNameRes(state.player.weapon.id))
                context.getString(
                    R.string.coach_finish_target,
                    nameById(context, state, msg.targetId),
                    msg.targetHp,
                    weaponOrSkill,
                )
            }
            is CoachMessage.FocusFinish -> {
                val weaponOrSkill = msg.skillId?.let { context.getString(skillNameRes(it)) }
                    ?: context.getString(weaponNameRes(state.player.weapon.id))
                context.getString(
                    R.string.coach_focus_finish,
                    nameById(context, state, msg.targetId),
                    msg.targetHp,
                    weaponOrSkill,
                )
            }
            is CoachMessage.DrinkHpPotion -> if (msg.lethal) {
                context.getString(R.string.coach_hp_lethal, msg.incoming, msg.hp)
            } else {
                context.getString(R.string.coach_hp_low, msg.hp)
            }
            is CoachMessage.DrinkMpPotion -> context.getString(
                R.string.coach_mp_low,
                msg.mp,
                msg.maxMp,
            )
            is CoachMessage.FocusHealer -> context.getString(
                R.string.coach_focus_healer,
                nameById(context, state, msg.healerId),
            )
            is CoachMessage.DodgeToCover -> context.getString(
                R.string.coach_dodge_cover,
                nameById(context, state, msg.threatId),
                msg.incoming,
                msg.after,
            )
            is CoachMessage.RaiseShield -> context.getString(
                if (msg.lethal) R.string.coach_shield_lethal else R.string.coach_shield_value,
                nameById(context, state, msg.threatId),
                msg.incoming,
                context.getString(skillNameRes(msg.skillId)),
                msg.saved,
            )
            is CoachMessage.UseControl -> context.getString(
                R.string.coach_control,
                context.getString(skillNameRes(msg.skillId)),
                nameById(context, state, msg.targetId),
            )
            is CoachMessage.CastAttack -> if (msg.blocked) {
                context.getString(
                    R.string.coach_attack_blocked,
                    nameById(context, state, msg.targetId),
                    context.getString(skillNameRes(msg.skillId)),
                )
            } else {
                context.getString(
                    R.string.coach_attack_skill,
                    nameById(context, state, msg.targetId),
                    context.getString(skillNameRes(msg.skillId)),
                    msg.damage,
                )
            }
            is CoachMessage.StrikeWeapon -> context.getString(
                R.string.coach_attack_weapon,
                nameById(context, state, msg.targetId),
                context.getString(weaponNameRes(state.player.weapon.id)),
            )
            is CoachMessage.SwitchToReachable -> context.getString(
                R.string.coach_switch_target,
                nameById(context, state, msg.targetId),
                nameById(context, state, msg.otherId),
            )
            is CoachMessage.ApproachTarget -> context.getString(
                R.string.coach_approach,
                nameById(context, state, msg.targetId),
                msg.distance,
            )
            is CoachMessage.PreemptiveShield -> context.getString(
                R.string.coach_preemptive_shield,
                nameById(context, state, msg.targetId),
                context.getString(skillNameRes(msg.skillId)),
            )
            CoachMessage.EndTurnAdvice -> context.getString(R.string.coach_end_turn)
        }
        return if (hint.showIntro) {
            context.getString(R.string.coach_intro_prefix, body)
        } else {
            body
        }
    }

    fun formatForecastLine(context: Context, line: ForecastLine): String {
        val sourceName = when (val src = line.source) {
            is ForecastSource.Skill -> context.getString(skillNameRes(src.skillId))
            is ForecastSource.Weapon -> context.getString(weaponNameRes(src.weaponId))
        }
        val tag = when {
            !line.inRange -> context.getString(R.string.tactical_preview_out_of_range)
            line.blocked -> context.getString(R.string.tactical_preview_blocked)
            line.kills -> context.getString(R.string.tactical_preview_kills, line.damage)
            line.covered -> context.getString(R.string.tactical_preview_covered, line.damage)
            else -> context.getString(R.string.tactical_preview_damage, line.damage)
        }
        return "$sourceName: $tag"
    }

    fun formatIntentSummary(context: Context, intent: EnemyIntent): String = when (intent.kind) {
        IntentKind.Attack -> context.getString(
            R.string.tactical_intent_attack,
            intent.expectedDamage ?: 0,
        )
        IntentKind.Control -> context.getString(R.string.tactical_intent_control)
        IntentKind.Shield -> context.getString(R.string.tactical_intent_shield)
        IntentKind.Mend -> context.getString(R.string.tactical_intent_mend)
        IntentKind.Command -> context.getString(R.string.tactical_intent_command)
        IntentKind.Cover -> context.getString(R.string.tactical_intent_cover)
        IntentKind.Approach -> context.getString(R.string.tactical_intent_approach)
        IntentKind.Stunned -> context.getString(R.string.tactical_intent_stunned)
        IntentKind.Idle -> context.getString(R.string.tactical_intent_idle)
    }

    fun intentBadgeSymbol(intent: EnemyIntent): String = when (intent.kind) {
        IntentKind.Attack -> "\u2694${intent.expectedDamage ?: ""}"
        IntentKind.Control -> "\u26D3"
        IntentKind.Shield -> "\uD83D\uDEE1"
        IntentKind.Mend -> "\u271A"
        IntentKind.Command -> "\uD83C\uDFAF"
        IntentKind.Cover -> "\u25E9"
        IntentKind.Approach -> "\u2192"
        IntentKind.Stunned -> "Z"
        IntentKind.Idle -> "\u00B7"
    }
}
