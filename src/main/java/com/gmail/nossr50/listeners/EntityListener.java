package com.gmail.nossr50.listeners;

import com.gmail.nossr50.config.WorldBlacklist;
import com.gmail.nossr50.core.MetadataConstants;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.subskills.interfaces.InteractType;
import com.gmail.nossr50.events.fake.FakeEntityDamageByEntityEvent;
import com.gmail.nossr50.events.fake.FakeEntityDamageEvent;
import com.gmail.nossr50.events.fake.FakeEntityTameEvent;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.party.PartyManager;
import com.gmail.nossr50.skills.archery.Archery;
import com.gmail.nossr50.skills.mining.BlastMining;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.skills.taming.Taming;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.CombatUtils;
import com.gmail.nossr50.worldguard.WorldGuardManager;
import com.gmail.nossr50.worldguard.WorldGuardUtils;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

public class EntityListener implements Listener {
    private final mcMMO plugin;

    public EntityListener(final mcMMO plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityTransform(EntityTransformEvent event) {
        //Transfer metadata keys from mob-spawned mobs to new mobs
        if (event.getEntity().getMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY) != null || event.getEntity().getMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY).size() >= 1) {
            for (Entity entity : event.getTransformedEntities()) {
                entity.setMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY, MetadataConstants.metadataValue);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTargetEntity(EntityTargetLivingEntityEvent event) {
        if (!mcMMO.getConfigManager().getConfigExploitPrevention().getEndermenEndermiteFix())
            return;

        //It's rare but targets can be null sometimes
        if (event.getTarget() == null) {
            return;
        }

        //Prevent entities from giving XP if they target endermite
        if (event.getTarget() instanceof Endermite) {
            if (event.getEntity().getMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY) == null || event.getEntity().getMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY).size() <= 0)
                event.getEntity().setMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY, MetadataConstants.metadataValue);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            /* WORLD GUARD MAIN FLAG CHECK */
            if (WorldGuardUtils.isWorldGuardLoaded()) {
                if (!WorldGuardManager.getInstance().hasMainFlag(player))
                    return;
            }
        }

        Entity projectile = event.getProjectile();

        if (!(projectile instanceof Arrow)) {
            return;
        }

        ItemStack bow = event.getBow();

        if (bow != null && bow.containsEnchantment(Enchantment.ARROW_INFINITE)) {
            projectile.setMetadata(MetadataConstants.INFINITE_ARROW_METAKEY, MetadataConstants.metadataValue);
        }

        projectile.setMetadata(MetadataConstants.BOW_FORCE_METAKEY,
                new FixedMetadataValue(plugin,
                        Math.min(event.getForce()
                                * mcMMO.getConfigManager().getConfigExperience().getExperienceArchery().getForceMultiplier(), 1.0)));
        projectile.setMetadata(MetadataConstants.ARROW_DISTANCE_METAKEY, new FixedMetadataValue(plugin, projectile.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            /* WORLD GUARD MAIN FLAG CHECK */
            if (WorldGuardUtils.isWorldGuardLoaded()) {
                if (!WorldGuardManager.getInstance().hasMainFlag(player))
                    return;
            }
        }

        Projectile projectile = event.getEntity();

        if (!(projectile instanceof Arrow) || projectile.hasMetadata(MetadataConstants.BOW_FORCE_METAKEY)) {
            return;
        }

        projectile.setMetadata(MetadataConstants.BOW_FORCE_METAKEY, new FixedMetadataValue(plugin, 1.0));
        projectile.setMetadata(MetadataConstants.ARROW_DISTANCE_METAKEY, new FixedMetadataValue(plugin, projectile.getLocation()));
    }

    /**
     * Monitor EntityChangeBlock events.
     *
     * @param event The event to watch
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        Block block = event.getBlock();

        // When the event is fired for the falling block that changes back to a
        // normal block
        // event.getBlock().getType() returns AIR
        if (!BlockUtils.shouldBeWatched(block.getState())
                && block.getState().getType() != Material.WATER
                && block.getType() != Material.AIR) {
            return;
        }

        Entity entity = event.getEntity();

        if (entity instanceof FallingBlock || entity instanceof Enderman) {
            boolean isTracked = entity.hasMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY);

            if (mcMMO.getPlaceStore().isTrue(block) && !isTracked) {
                mcMMO.getPlaceStore().setFalse(block);
                entity.setMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY, MetadataConstants.metadataValue);
            } else if (isTracked) {
                mcMMO.getPlaceStore().setTrue(block);
            }
        } else if ((block.getType() == Material.REDSTONE_ORE)) {
        } else {
            if (mcMMO.getPlaceStore().isTrue(block)) {
                mcMMO.getPlaceStore().setFalse(block);
            }
        }
    }

    /*@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageDebugLowest(EntityDamageEvent event)
    {
        if(event instanceof FakeEntityDamageByEntityEvent)
            return;

        if(event instanceof FakeEntityDamageEvent)
            return;

        Bukkit.broadcastMessage(ChatColor.DARK_AQUA+"DMG Before Events: "
                +ChatColor.RESET+event.getDamage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageDebugMonitor(EntityDamageEvent event)
    {
        if(event instanceof FakeEntityDamageByEntityEvent)
            return;

        if(event instanceof FakeEntityDamageEvent)
            return;

        if(!(event.getEntity() instanceof LivingEntity))
            return;

        LivingEntity entity = (LivingEntity) event.getEntity();

        double rawDamage = event.getDamage();
        double dmgAfterReduction = event.getFinalDamage();

        Bukkit.broadcastMessage(ChatColor.GOLD+"DMG After Events: "
                + event.getEntity().getName()+ChatColor.RESET
                +"RawDMG["+rawDamage+"], "
                +"FinalDMG=["+dmgAfterReduction+"]");

        Bukkit.broadcastMessage(
                event.getEntity().getName()
                +ChatColor.GREEN
                +" HP "
                +ChatColor.RESET
                +entity.getHealth()
                +ChatColor.YELLOW
                +" -> "
                +ChatColor.RESET
                +(entity.getHealth()-event.getFinalDamage()));

        Bukkit.broadcastMessage("");
    }*/

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageLowest(EntityDamageByEntityEvent event) {
        Entity defender = event.getEntity();

        if (defender.getMetadata(MetadataConstants.CUSTOM_DAMAGE_METAKEY).size() > 0) {
            defender.removeMetadata(MetadataConstants.CUSTOM_DAMAGE_METAKEY, plugin);

            if (defender instanceof Player) {
                LivingEntity defLive = (LivingEntity) defender;
                defLive.setHealth(Math.max(0, (defLive.getHealth() - event.getFinalDamage())));
                event.setCancelled(true);
            }

        }
    }

    /**
     * Handle EntityDamageByEntity events that involve modifying the event.
     *
     * @param event The event to watch
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        double damage = event.getFinalDamage();
        Entity defender = event.getEntity();
        Entity attacker = event.getDamager();

        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        if (event instanceof FakeEntityDamageByEntityEvent) {
            return;
        }

        if (event.getEntity() instanceof ArmorStand) {
            return;
        }

        if (event.getDamager().hasMetadata(MetadataConstants.SPAWNED_FIREWORKS_METAKEY)) {
            event.setCancelled(true);
            return;
        }

        if (attacker instanceof Player) {
            Player player = (Player) attacker;

            /* WORLD GUARD MAIN FLAG CHECK */
            if (WorldGuardUtils.isWorldGuardLoaded()) {
                if (!WorldGuardManager.getInstance().hasMainFlag(player))
                    return;
            }
        }

        if (damage <= 0) {
            if (defender instanceof Player && attacker instanceof Player) {
                Player defendingPlayer = (Player) defender;
                Player attackingPlayer = (Player) attacker;
                if (event.getDamage(DamageModifier.ABSORPTION) > 0) {
                    //If friendly fire is off don't allow players to hurt one another
                    if (!mcMMO.getConfigManager().getConfigParty().isPartyFriendlyFireEnabled())
                        if ((PartyManager.inSameParty(defendingPlayer, attackingPlayer) || PartyManager.areAllies(defendingPlayer, attackingPlayer)) && !(Permissions.friendlyFire(attackingPlayer) && Permissions.friendlyFire(defendingPlayer))) {
                            event.setCancelled(true);
                            return;
                        }
                }
            }
            return;
        }


        /*
        As far as I can tell at one point we registered meta-data about custom damage and we no longer do that.

        if (defender.hasMetadata(mcMMO.customDamageKey)) {
            defender.removeMetadata(mcMMO.customDamageKey, plugin);
            return;
        }
        */

        if (Misc.isNPCEntity(defender) || !defender.isValid() || !(defender instanceof LivingEntity)) {
            return;
        }

        LivingEntity target = (LivingEntity) defender;

        if (CombatUtils.isInvincible(target, damage)) {
            return;
        }

        if (Misc.isNPCEntity(attacker)) {
            return;
        }

        if (attacker instanceof Projectile) {
            ProjectileSource projectileSource = ((Projectile) attacker).getShooter();

            if (projectileSource instanceof LivingEntity) {
                attacker = (LivingEntity) projectileSource;
            }

            if(defender instanceof Player) {
                Player playerDefender = (Player) defender;
                UnarmedManager unarmedManager = UserManager.getPlayer(playerDefender).getUnarmedManager();

                if (unarmedManager.canDeflect()) {
                    if(unarmedManager.deflectCheck()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
        else if (attacker instanceof Tameable) {
            AnimalTamer animalTamer = ((Tameable) attacker).getOwner();

            if (animalTamer != null && ((OfflinePlayer) animalTamer).isOnline()) {
                attacker = (Entity) animalTamer;
            }
        } else if (attacker instanceof TNTPrimed && defender instanceof Player) {
            if (BlastMining.processBlastMiningExplosion(event, (TNTPrimed) attacker, (Player) defender)) {
                return;
            }
        }

        if (defender instanceof Player && attacker instanceof Player) {
            Player defendingPlayer = (Player) defender;
            Player attackingPlayer = (Player) attacker;

            if (!UserManager.hasPlayerDataKey(defendingPlayer) || !UserManager.hasPlayerDataKey(attackingPlayer)) {
                return;
            }

            // We want to make sure we're not gaining XP or applying abilities
            // when we hit ourselves
            if (defendingPlayer.equals(attackingPlayer)) {
                return;
            }

            //Party Friendly Fire
            if (!mcMMO.getConfigManager().getConfigParty().isPartyFriendlyFireEnabled())
                if ((PartyManager.inSameParty(defendingPlayer, attackingPlayer)
                        || PartyManager.areAllies(defendingPlayer, attackingPlayer)) && !(Permissions.friendlyFire(attackingPlayer) && Permissions.friendlyFire(defendingPlayer))) {
                    event.setCancelled(true);
                    return;
                }
        }

        CombatUtils.processCombatAttack(event, attacker, target);
        CombatUtils.handleHealthbars(attacker, target, event.getFinalDamage(), plugin);

        /**
         * This sets entity names back to whatever they are supposed to be
         */
        if (event.getFinalDamage() >= target.getHealth()) {
            if (attacker instanceof LivingEntity) {
                CombatUtils.fixNames((LivingEntity) attacker);
            }

            CombatUtils.fixNames(target);
        }

    }

    /**
     * Handle EntityDamage events that involve modifying the event.
     *
     * @param event The event to modify
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            /* WORLD GUARD MAIN FLAG CHECK */
            if (WorldGuardUtils.isWorldGuardLoaded()) {
                if (!WorldGuardManager.getInstance().hasMainFlag(player))
                    return;
            }
        }

        /*
         * Process Registered Interactions
         */

        InteractionManager.processEvent(event, plugin, InteractType.ON_ENTITY_DAMAGE);

        /*
         * Old code
         */

        if (event instanceof FakeEntityDamageEvent) {
            return;
        }

        double damage = event.getFinalDamage();

        if (damage <= 0) {
            return;
        }

        Entity entity = event.getEntity();

        /*
        As far as I can tell at one point we registered meta-data about custom damage and we no longer do that.
        if (entity.hasMetadata(mcMMO.customDamageKey)) {
            entity.removeMetadata(mcMMO.customDamageKey, plugin);
            return;
        }
        */

        if (Misc.isNPCEntity(entity) || !entity.isValid() || !(entity instanceof LivingEntity)) {
            return;
        }

        LivingEntity livingEntity = (LivingEntity) entity;

        if (CombatUtils.isInvincible(livingEntity, damage)) {
            return;
        }

        DamageCause cause = event.getCause();

        if (livingEntity instanceof Player) {
            Player player = (Player) entity;

            if (!UserManager.hasPlayerDataKey(player)) {
                return;
            }

            McMMOPlayer mcMMOPlayer = UserManager.getPlayer(player);

            //Profile not loaded
            if (mcMMOPlayer == null)
                return;

            /* Check for invincibility */
            if (mcMMOPlayer.getGodMode()) {
                event.setCancelled(true);
                return;
            }

            if (event.getFinalDamage() >= 1) {
                mcMMOPlayer.actualizeRecentlyHurt();
            }

        } else if (livingEntity instanceof Tameable) {
            Tameable pet = (Tameable) livingEntity;
            AnimalTamer owner = pet.getOwner();

            if (owner instanceof Player) {
                Player player = (Player) owner;
                /* WORLD GUARD MAIN FLAG CHECK */
                if (WorldGuardUtils.isWorldGuardLoaded()) {
                    if (!WorldGuardManager.getInstance().hasMainFlag(player))
                        return;
                }
            }

            if (Taming.getInstance().canPreventDamage(pet, owner)) {
                Player player = (Player) owner;
                Wolf wolf = (Wolf) pet;

                //Profile not loaded
                if (UserManager.getPlayer(player) == null) {
                    return;
                }

                TamingManager tamingManager = UserManager.getPlayer(player).getTamingManager();

                switch (cause) {
                    case CONTACT:
                    case FIRE:
                    case LAVA:
                        if (tamingManager.canUseEnvironmentallyAware()) {
                            tamingManager.processEnvironmentallyAware(wolf, event.getDamage());
                        }
                        return;

                    case FALL:
                        if (tamingManager.canUseEnvironmentallyAware()) {
                            event.setCancelled(true);
                        }
                        return;

                    case ENTITY_ATTACK:
                    case PROJECTILE:
                        if (tamingManager.canUseThickFur()) {
                            event.setDamage(Taming.getInstance().processThickFur(wolf, event.getDamage()));

                            if (event.getFinalDamage() == 0) {
                                event.setCancelled(true);
                            }
                        }
                        return;

                    case FIRE_TICK:
                        if (tamingManager.canUseThickFur()) {
                            Taming.getInstance().processThickFurFire(wolf);
                        }
                        return;

                    case MAGIC:
                    case POISON:
                    case WITHER:
                        if (tamingManager.canUseHolyHound()) {
                            Taming.getInstance().processHolyHound(wolf, event.getDamage());
                        }
                        return;

                    case BLOCK_EXPLOSION:
                    case ENTITY_EXPLOSION:
                    case LIGHTNING:
                        if (tamingManager.canUseShockProof()) {
                            event.setDamage(Taming.getInstance().processShockProof(wolf, event.getDamage()));

                            if (event.getFinalDamage() == 0) {
                                event.setCancelled(true);
                            }
                        }
                        return;

                    default:
                }
            }
        }
    }

    /**
     * Monitor EntityDeath events.
     *
     * @param event The event to watch
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDeathLowest(EntityDeathEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        LivingEntity entity = event.getEntity();

        if (Misc.isNPCEntity(entity)) {
            return;
        }

        if (entity.hasMetadata(MetadataConstants.CUSTOM_NAME_METAKEY)) {
            entity.setCustomName(entity.getMetadata(MetadataConstants.CUSTOM_NAME_METAKEY).get(0).asString());
            entity.removeMetadata(MetadataConstants.CUSTOM_NAME_METAKEY, plugin);
        }

        if (entity.hasMetadata(MetadataConstants.NAME_VISIBILITY_METAKEY)) {
            entity.setCustomNameVisible(entity.getMetadata(MetadataConstants.NAME_VISIBILITY_METAKEY).get(0).asBoolean());
            entity.removeMetadata(MetadataConstants.NAME_VISIBILITY_METAKEY, plugin);
        }

        if (entity.hasMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY)) {
            entity.removeMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY, plugin);
        }
    }

    /**
     * Monitor EntityDeath events.
     *
     * @param event The event to watch
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        LivingEntity entity = event.getEntity();

        if (Misc.isNPCEntity(entity)) {
            return;
        }

        Archery.arrowRetrievalCheck(entity);
    }

    /**
     * Monitor CreatureSpawn events.
     *
     * @param event The event to watch
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        LivingEntity entity = event.getEntity();

        switch (event.getSpawnReason()) {
            case NETHER_PORTAL:
            case SPAWNER:
            case SPAWNER_EGG:
                if (mcMMO.getConfigManager().getConfigExploitPrevention().doSpawnedEntitiesGiveModifiedXP()) {
                    entity.setMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY, MetadataConstants.metadataValue);

                    Entity passenger = entity.getPassenger();

                    if (passenger != null) {
                        passenger.setMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY, MetadataConstants.metadataValue);
                    }
                }
                return;

            case BREEDING:
                entity.setMetadata(MetadataConstants.PETS_ANIMAL_TRACKING_METAKEY, MetadataConstants.metadataValue);
                return;

            default:
        }
    }

    /**
     * Handle ExplosionPrime events that involve modifying the event.
     *
     * @param event The event to modify
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        Entity entity = event.getEntity();

        if (!(entity instanceof TNTPrimed) || !entity.hasMetadata(MetadataConstants.TNT_TRACKING_METAKEY)) {
            return;
        }

        // We can make this assumption because we (should) be the only ones
        // using this exact metadata
        Player player = plugin.getServer().getPlayerExact(entity.getMetadata(MetadataConstants.TNT_TRACKING_METAKEY).get(0).asString());

        if (!UserManager.hasPlayerDataKey(player)) {
            return;
        }

        //Profile not loaded
        if (UserManager.getPlayer(player) == null) {
            return;
        }

        /* WORLD GUARD MAIN FLAG CHECK */
        if (WorldGuardUtils.isWorldGuardLoaded()) {
            if (!WorldGuardManager.getInstance().hasMainFlag(player))
                return;
        }

        MiningManager miningManager = UserManager.getPlayer(player).getMiningManager();

        if (miningManager.canUseBiggerBombs()) {
            event.setRadius(miningManager.biggerBombs(event.getRadius()));
        }
    }

    /**
     * Handle EntityExplode events that involve modifying the event.
     *
     * @param event The event to modify
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnitityExplode(EntityExplodeEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        Entity entity = event.getEntity();

        if (!(entity instanceof TNTPrimed) || !entity.hasMetadata(MetadataConstants.TNT_TRACKING_METAKEY)) {
            return;
        }

        // We can make this assumption because we (should) be the only ones
        // using this exact metadata
        Player player = plugin.getServer().getPlayerExact(entity.getMetadata(MetadataConstants.TNT_TRACKING_METAKEY).get(0).asString());

        if (!UserManager.hasPlayerDataKey(player)) {
            return;
        }

        /* WORLD GUARD MAIN FLAG CHECK */
        if (WorldGuardUtils.isWorldGuardLoaded()) {
            if (!WorldGuardManager.getInstance().hasMainFlag(player))
                return;
        }

        //Profile not loaded
        if (UserManager.getPlayer(player) == null) {
            return;
        }

        MiningManager miningManager = UserManager.getPlayer(player).getMiningManager();

        if (miningManager.canUseBlastMining()) {
            miningManager.blastMiningDropProcessing(event.getYield(), event.blockList());
            event.setYield(0);
        }
    }

    /**
     * Handle EntityExplode events that involve modifying the event.
     *
     * @param event The event to modify
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplodeMonitor(EntityExplodeEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        Entity entity = event.getEntity();

        if (!(entity instanceof TNTPrimed) || !entity.hasMetadata(MetadataConstants.SAFE_TNT_METAKEY)) {
            return;
        }

        event.blockList().clear();
    }

    /**
     * Handle FoodLevelChange events that involve modifying the event.
     *
     * @param event The event to modify
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        Entity entity = event.getEntity();

        if (!(entity instanceof Player)) {
            return;
        }

        Player player = (Player) entity;

        //Profile not loaded
        if (UserManager.getPlayer(player) == null) {
            return;
        }

        /* WORLD GUARD MAIN FLAG CHECK */
        if (WorldGuardUtils.isWorldGuardLoaded()) {
            if (!WorldGuardManager.getInstance().hasMainFlag(player))
                return;
        }

        if (!UserManager.hasPlayerDataKey(player)) {
            return;
        }

        int currentFoodLevel = player.getFoodLevel();
        int newFoodLevel = event.getFoodLevel();
        int foodChange = newFoodLevel - currentFoodLevel;

        if (foodChange <= 0) {
            return;
        }

        //Determine which hand is eating food
        //The main hand is used over the off hand if they both have food, so check the main hand first
        Material foodInHand;

        if(mcMMO.getMaterialMapStore().isFood(player.getInventory().getItemInMainHand().getType())) {
            foodInHand = player.getInventory().getItemInMainHand().getType();
        } else if(mcMMO.getMaterialMapStore().isFood(player.getInventory().getItemInOffHand().getType())) {
            foodInHand = player.getInventory().getItemInOffHand().getType();
        } else {
            return; //Not Food
        }

        /*
         * Some foods have 3 ranks Some foods have 5 ranks The number of ranks
         * is based on how 'common' the item is We can adjust this quite easily
         * if we find something is giving too much of a bonus
         */
        switch (foodInHand) {
            case BAKED_POTATO: /*
             * RESTORES 3 HUNGER - RESTORES 5 1/2 HUNGER @
             * 1000
             */
            case BEETROOT:
            case BREAD: /* RESTORES 2 1/2 HUNGER - RESTORES 5 HUNGER @ 1000 */
            case CARROT: /*
             * RESTORES 2 HUNGER - RESTORES 4 1/2 HUNGER @
             * 1000
             */
            case GOLDEN_CARROT: /*
             * RESTORES 3 HUNGER - RESTORES 5 1/2 HUNGER @
             * 1000
             */
            case MUSHROOM_STEW: /*
             * RESTORES 4 HUNGER - RESTORES 6 1/2 HUNGER @
             * 1000
             */
            case PUMPKIN_PIE: /*
             * RESTORES 4 HUNGER - RESTORES 6 1/2 HUNGER @
             * 1000
             */

            case COOKIE: /* RESTORES 1/2 HUNGER - RESTORES 2 HUNGER @ 1000 */
            case MELON_SLICE: /* RESTORES 1 HUNGER - RESTORES 2 1/2 HUNGER @ 1000 */
            case POISONOUS_POTATO: /*
             * RESTORES 1 HUNGER - RESTORES 2 1/2 HUNGER
             * @ 1000
             */
            case POTATO: /* RESTORES 1/2 HUNGER - RESTORES 2 HUNGER @ 1000 */
                if (Permissions.isSubSkillEnabled(player, SubSkillType.HERBALISM_FARMERS_DIET)) {
                    event.setFoodLevel(UserManager.getPlayer(player).getHerbalismManager().farmersDiet(newFoodLevel));
                }
                return;
            case COD:
            case SALMON:
            case TROPICAL_FISH:
            case COOKED_COD:
            case COOKED_SALMON:
                if (Permissions.isSubSkillEnabled(player, SubSkillType.FISHING_FISHERMANS_DIET)) {
                    event.setFoodLevel(UserManager.getPlayer(player).getFishingManager().handleFishermanDiet(newFoodLevel));
                }
                return;

            default:
        }
    }

    /**
     * Monitor EntityTame events.
     *
     * @param event The event to watch
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        if (event instanceof FakeEntityTameEvent) {
            return;
        }

        Player player = (Player) event.getOwner();

        /* WORLD GUARD MAIN FLAG CHECK */
        if (WorldGuardUtils.isWorldGuardLoaded()) {
            if (!WorldGuardManager.getInstance().hasMainFlag(player))
                return;
        }

        LivingEntity entity = event.getEntity();

        if (!UserManager.hasPlayerDataKey(player) || Misc.isNPCEntity(entity) || entity.hasMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY)) {
            return;
        }

        if (mcMMO.getConfigManager().getConfigExploitPrevention().doTamedEntitiesGiveXP())
            entity.setMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY, MetadataConstants.metadataValue);

        //Profile not loaded
        //TODO: Redundant
        /*if(UserManager.getPlayer(player) == null)
        {
            return;
        }*/

        UserManager.getPlayer(player).getTamingManager().awardTamingXP(entity);

    }

    /**
     * Handle EntityTarget events.
     *
     * @param event The event to process
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        Entity entity = event.getEntity();
        Entity target = event.getTarget();

        if (!(entity instanceof Tameable) || !(target instanceof Player)) {
            return;
        }

        Player player = (Player) target;

        /* WORLD GUARD MAIN FLAG CHECK */
        if (WorldGuardUtils.isWorldGuardLoaded()) {
            if (!WorldGuardManager.getInstance().hasMainFlag(player))
                return;
        }

        Tameable tameable = (Tameable) entity;

        if (!UserManager.hasPlayerDataKey(player) || !CombatUtils.isFriendlyPet(player, tameable)) {
            return;
        }

        // isFriendlyPet ensures that the Tameable is: Tamed, owned by a player,
        // and the owner is in the same party
        // So we can make some assumptions here, about our casting and our check
        if (!(Permissions.friendlyFire(player) && Permissions.friendlyFire((Player) tameable.getOwner()))) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle PotionSplash events in order to fix broken Splash Potion of
     * Saturation.
     *
     * @param event The event to process
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        for (PotionEffect effect : ((PotionMeta) event.getPotion().getItem().getItemMeta()).getCustomEffects()) {
            if (!effect.getType().equals(PotionEffectType.SATURATION)) {
                return;
            }

            for (LivingEntity entity : event.getAffectedEntities()) {
                int duration = (int) (effect.getDuration() * event.getIntensity(entity));
                entity.addPotionEffect(new PotionEffect(effect.getType(), duration, effect.getAmplifier(), effect.isAmbient()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPigZapEvent(PigZapEvent event) {
        /* WORLD BLACKLIST CHECK */
        if (WorldBlacklist.isWorldBlacklisted(event.getEntity().getWorld()))
            return;

        if (event.getEntity().hasMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY)) {
            event.getPigZombie().setMetadata(MetadataConstants.UNNATURAL_MOB_METAKEY, MetadataConstants.metadataValue);
        }
    }
}
