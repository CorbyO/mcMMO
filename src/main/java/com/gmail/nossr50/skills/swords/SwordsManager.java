package com.gmail.nossr50.skills.swords;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.meta.RuptureTaskMeta;
import com.gmail.nossr50.datatypes.skills.AbilityToolType;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.runnables.skills.RuptureTask;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.random.RandomChanceUtil;
import com.gmail.nossr50.util.skills.CombatUtils;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillActivationType;
import com.neetgames.mcmmo.player.OnlineMMOPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class SwordsManager extends SkillManager {
    public SwordsManager(OnlineMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.SWORDS);
    }

    public boolean canActivateAbility() {
        return mmoPlayer.getSuperAbilityManager().isAbilityToolPrimed(AbilityToolType.SERRATED_STRIKES_TOOL) && Permissions.serratedStrikes(getPlayer());
    }

    public boolean canUseStab() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SWORDS_STAB) && RankUtils.hasUnlockedSubskill(getPlayer(), SubSkillType.SWORDS_STAB);
    }

    public boolean canUseRupture() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SWORDS_RUPTURE) && RankUtils.hasUnlockedSubskill(getPlayer(), SubSkillType.SWORDS_RUPTURE);
    }

    public boolean canUseCounterAttack(Entity target) {
        if(!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWORDS_COUNTER_ATTACK))
            return false;

        return target instanceof LivingEntity && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SWORDS_COUNTER_ATTACK);
    }

    public boolean canUseSerratedStrike() {
        if(!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWORDS_SERRATED_STRIKES))
            return false;

        return mmoPlayer.getSuperAbilityManager().getAbilityMode(SuperAbilityType.SERRATED_STRIKES);
    }

    /**
     * Check for Bleed effect.
     *
     * @param target The defending entity
     */
    public void processRupture(@NotNull LivingEntity target) {
        if(target.hasMetadata(mcMMO.RUPTURE_META_KEY)) {
            RuptureTaskMeta ruptureTaskMeta = (RuptureTaskMeta) target.getMetadata(mcMMO.RUPTURE_META_KEY).get(0);

            if(mmoPlayer.isDebugMode()) {
                mmoPlayer.getPlayer().sendMessage("Rupture task ongoing for target " + target.toString());
                mmoPlayer.getPlayer().sendMessage(ruptureTaskMeta.getRuptureTimerTask().toString());
            }

            ruptureTaskMeta.getRuptureTimerTask().refreshRupture();
            return; //Don't apply bleed
        }

        if (RandomChanceUtil.rollDice(AdvancedConfig.getInstance().getRuptureChanceToApplyOnHit(getRuptureRank()), 100)) {

            if (target instanceof Player) {
                Player defender = (Player) target;

                //Don't start or add to a bleed if they are blocking
                if(defender.isBlocking())
                    return;

                if (NotificationManager.doesPlayerUseNotifications(defender)) {
                    NotificationManager.sendPlayerInformation(defender, NotificationType.SUBSKILL_MESSAGE, "Swords.Combat.Bleeding.Started");
                }
            }

            RuptureTask ruptureTask = new RuptureTask(mmoPlayer, target,
                    AdvancedConfig.getInstance().getRuptureTickDamage(target instanceof Player, getRuptureRank()),
                    AdvancedConfig.getInstance().getRuptureExplosionDamage(target instanceof Player, getRuptureRank()));

            RuptureTaskMeta ruptureTaskMeta = new RuptureTaskMeta(mcMMO.p, ruptureTask);

            ruptureTask.runTaskTimer(mcMMO.p, 0, 1);
            target.setMetadata(mcMMO.RUPTURE_META_KEY, ruptureTaskMeta);

//            if (mmoPlayer.hasSkillChatNotifications()) {
//                NotificationManager.sendPlayerInformation(getPlayer(), NotificationType.SUBSKILL_MESSAGE, "Swords.Combat.Bleeding");
//            }
        }
    }

    private int getRuptureRank() {
        return RankUtils.getRank(getPlayer(), SubSkillType.SWORDS_RUPTURE);
    }

    public double getStabDamage()
    {
        int rank = RankUtils.getRank(getPlayer(), SubSkillType.SWORDS_STAB);

        if(rank > 0)
        {
            return (1.0D + (rank * 1.5));
        }

        return 0;
    }

    public int getToolTier(@NotNull ItemStack itemStack)
    {
        if(ItemUtils.isNetheriteTool(itemStack))
            return 5;
        if(ItemUtils.isDiamondTool(itemStack))
            return 4;
        else if(ItemUtils.isIronTool(itemStack) || ItemUtils.isGoldTool(itemStack))
            return 3;
        else if(ItemUtils.isStoneTool(itemStack))
            return 2;
        else
            return 1;
    }

    public int getRuptureBleedTicks(boolean isTargetPlayer) {
        return AdvancedConfig.getInstance().getRuptureDurationSeconds(isTargetPlayer) / RuptureTask.DAMAGE_TICK_INTERVAL;
    }

    /**
     * Handle the effects of the Counter Attack ability
     *
     * @param attacker The {@link LivingEntity} being affected by the ability
     * @param damage The amount of damage initially dealt by the event
     */
    public void counterAttackChecks(@NotNull LivingEntity attacker, double damage) {
        if (RandomChanceUtil.isActivationSuccessful(SkillActivationType.RANDOM_LINEAR_100_SCALE_WITH_CAP, SubSkillType.SWORDS_COUNTER_ATTACK, getPlayer())) {
            CombatUtils.dealDamage(attacker, damage / Swords.counterAttackModifier, getPlayer());

            NotificationManager.sendPlayerInformation(getPlayer(), NotificationType.SUBSKILL_MESSAGE, "Swords.Combat.Countered");

            if (attacker instanceof Player) {
                NotificationManager.sendPlayerInformation((Player)attacker, NotificationType.SUBSKILL_MESSAGE, "Swords.Combat.Counter.Hit");
            }
        }
    }

    /**
     * Handle the effects of the Serrated Strikes ability
     *
     * @param target The {@link LivingEntity} being affected by the ability
     * @param damage The amount of damage initially dealt by the event
     */
    public void serratedStrikes(@NotNull LivingEntity target, double damage, Map<DamageModifier, Double> modifiers) {
        CombatUtils.applyAbilityAoE(getPlayer(), target, damage / Swords.serratedStrikesModifier, modifiers, skill);
    }
}
