/*
 * Limited Creative - (Bukkit Plugin)
 * Copyright (C) 2012 jascha@ja-s.de
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.jaschastarke.minecraft.limitedcreative;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.material.Lever;

import de.jaschastarke.minecraft.utils.IPermission;
import de.jaschastarke.minecraft.worldguard.events.PlayerChangedAreaEvent;
import static de.jaschastarke.minecraft.utils.Locale.L;

public class LCPlayer {
    private static Core plugin = Core.plugin;
    
    //private Player player;
    private String name;
    private Inventory _inv;
    private GameMode _permanent_gamemode = null;
    private long _timestamp;
    
    public LCPlayer(Player player) {
        //this.player = player;
        name = player.getName();
        touch();
        
        if (!this.isRegionGameMode(player.getGameMode())) {
            setPermanentGameMode(player.getGameMode());
        }
    }
    /*private void updatePlayer(Player player) {
        this.player = player;
        _inv = null;
    }*/
    
    public Player getPlayer() {
        //return player;
        return plugin.getServer().getPlayerExact(name);
    }
    public String getName() {
        return name;
    }
    
    public Inventory getInv() {
        if (_inv == null)
            _inv = new Inventory(this);
        return _inv;
    }
    
    public void touch() {
        _timestamp = System.currentTimeMillis();
    }
    public boolean isOutdated() {
        return (getPlayer() == null || !getPlayer().isOnline()) &&
                 _timestamp < (System.currentTimeMillis() - Players.CLEANUP_TIMEOUT);
    }

    private Map<String, Object> options = new HashMap<String, Object>();
    public void setRegionGameMode(final GameMode gm) {
        options.remove("region");
        Core.debug(getName()+": set region game mode: " + gm);
        Players.getOptions().setRegionGameMode(getName(), gm);
    }
    
    private GameMode getRegionGameMode() {
        if (!options.containsKey("region")) {
            options.put("region", Players.getOptions().getRegionGameMode(getName()));
        }
        Core.debug(getName()+": get region game mode: " + options.get("region"));
        return (GameMode) options.get("region");
    }
    public boolean isRegionGameMode(final GameMode gm) {
        return gm.equals(getRegionGameMode());
    }
    public boolean isRegionGameMode() {
        return getRegionGameMode() != null;
    }
    
    public boolean isOptionalRegionGameMode() {
        return getOptionalRegionGameMode() != null;
    }
    public boolean isOptionalRegionGameMode(final GameMode gm) {
        return gm.equals(getOptionalRegionGameMode());
    }
    public boolean isOptionalRegionGameMode(final String region, final GameMode gm) {
        return gm.equals(getOptionalRegionGameMode(region));
    }
    private GameMode getOptionalRegionGameMode() {
        String region = plugin.worldguard.getRegionManager().getRegionsHash(getPlayer().getLocation());
        return getOptionalRegionGameMode(region);
    }
    private GameMode getOptionalRegionGameMode(String region) {
        if (!options.containsKey("region_opt#"+region)) {
            options.put("region_opt#"+region, Players.getOptions().getOptionalRegionGameMode(getName(), region));
        }
        Core.debug(getName()+": get optional region game mode: "+region+" - " + options.get("region_opt#"+region));
        return (GameMode) options.get("region_opt#"+region);
    }
    
    public void setOptionalRegionGameMode(GameMode gm) {
        String region = plugin.worldguard.getRegionManager().getRegionsHash(getPlayer().getLocation());
        setOptionalRegionGameMode(region, gm);
    }
    public void setOptionalRegionGameMode(String region, GameMode gm) {
        options.remove("region_opt#"+region);
        Core.debug(getName()+": set optional region game mode: "+region+" - " + gm);
        Players.getOptions().setOptionalRegionGameMode(getName(), region, gm);
    }
    
    public void setPermanentGameMode(GameMode temp) {
        Core.debug(getName()+": set permanent game mode: " + temp);
        if (temp != null) {
            if (temp.equals(plugin.com.getDefaultGameMode(getPlayer().getWorld()))) {
                temp = null;
            } else {
                setRegionGameMode(null);
            }
        }
        _permanent_gamemode = temp;
    }
    public boolean isPermanentGameMode(GameMode temp) {
        Core.debug(getName()+": get permanent game mode: " + _permanent_gamemode);
        return temp.equals(_permanent_gamemode);
    }
    
    public boolean onSetGameMode(GameMode gm) {
        Core.debug(getName() + " going into " + gm);
        if (isRegionGameMode()) { // change to the other gamemode as the area defines
            if (!isRegionGameMode(gm)) { // only when we are not switching to the mode the region allows
                if (!plugin.config.getRegionOptional()) {
                    getPlayer().sendMessage(ChatColor.RED + L("exception.region.not_optional", gm.toString().toLowerCase()));
                    Core.debug("... denied");
                    return false;
                } else {
                    setOptionalRegionGameMode(gm);
                }
            } else {
                // we are changing to the mode the region defines, thats not permanent
                setOptionalRegionGameMode(null);
                setPermanentGameMode(null);
            }
        } else {
            setPermanentGameMode(gm); // we are not in a region, so the mode change is permanent
        }
        
        /*
         * Feature 1: Separated Inventories / Storage
         */
        if (plugin.config.getStoreEnabled()) {
            if (plugin.config.getPermissionToKeepInventory() && hasPermission(Perms.KEEPINVENTORY))
                return true;
            if (gm != GameMode.CREATIVE || plugin.config.getStoreCreative())
                getInv().save();
            if (gm == GameMode.CREATIVE) {
                if (plugin.config.getStoreCreative() && getInv().isStored(GameMode.CREATIVE)) {
                    getInv().load(GameMode.CREATIVE);
                } else {
                    getInv().clear();
                }
            } else if (gm == GameMode.SURVIVAL) {
                if (getInv().isStored(GameMode.SURVIVAL))
                    getInv().load(GameMode.SURVIVAL);
            }
            
        }
        return true;
    }
    
    public void onDropItem(PlayerDropItemEvent event) {
        Core.debug(getName() + " ("+getPlayer().getGameMode()+")  drops items " + event.getItemDrop().getItemStack().getType());
        if (getPlayer().getGameMode() == GameMode.CREATIVE) {
            if (plugin.config.getPermissionsEnabled() && hasPermission(Perms.NoLimit.DROP))
                return;
            Core.debug("removed");
            if (plugin.config.getRemoveDrop())
                event.getItemDrop().remove();
            else
                event.setCancelled(true);
        }
    }
    public void onPickupItem(PlayerPickupItemEvent event) {
        if (getPlayer().getGameMode() == GameMode.CREATIVE) {
            if (plugin.config.getPermissionsEnabled() && hasPermission(Perms.NoLimit.PICKUP))
                return;
            if (plugin.config.getBlockPickupInCreative()) {
                event.setCancelled(true);
            } else if(plugin.config.getRemovePickup()) {
                event.getItem().remove();
                event.setCancelled(true);
            }
        }
    }
    
    public void onDie(EntityDeathEvent event) {
        if (getPlayer().getGameMode() == GameMode.CREATIVE) {
            if (!plugin.config.getPermissionsEnabled() || !hasPermission(Perms.NoLimit.DROP)) {
                event.getDrops().clear();
                //getInv().storeTemp();
            }
        }
    }
    /* removed, because much to insecure. also we can save memory with out this
    public void onRespawn(PlayerRespawnEvent event) {
        if (getPlayer().getGameMode() == GameMode.CREATIVE) {
            if (!plugin.config.getPermissionsEnabled() || !hasPermission(Perms.NoLimit.DROP)) {
                getInv().restoreTemp();
            }
        }
        getInv().clearTemp();
    }*/
    
    public void onDamage(Entity from, EntityDamageByEntityEvent event) { // receives damage
        if (from instanceof Player) {
            // its PVP
            Player attacker = (Player) event.getDamager();
            if (attacker.getGameMode() == GameMode.CREATIVE) {
                if (!plugin.config.getPermissionsEnabled() || !Players.get(attacker).hasPermission(Perms.NoLimit.PVP)) {
                    event.setCancelled(true);
                    return; // skip next check
                }
            }
            if (getPlayer().getGameMode() == GameMode.CREATIVE) {
                if (!plugin.config.getPermissionsEnabled() || !hasPermission(Perms.NoLimit.PVP)) {
                    event.setCancelled(true);
                }
            }
        }
    }
    public void onDealDamage(EntityDamageByEntityEvent event) { // deals damage
        if (event.getEntity() instanceof Creature) {
            if (getPlayer().getGameMode() == GameMode.CREATIVE && plugin.config.getMobDamageBlock()) {
                if (!plugin.config.getPermissionsEnabled() || !hasPermission(Perms.NoLimit.MOB_DAMAGE)) {
                    event.setCancelled(true);
                }
            }
        }
    }
    
    /**
     * don't let the player be target by creatures he can't kill
     */
    public void onTarget(EntityTargetEvent event) {
        if (event.getEntity() instanceof Creature) {
            if (((Player) event.getTarget()).getGameMode() == GameMode.CREATIVE && plugin.config.getMobDamageBlock()) {
                if (!plugin.config.getPermissionsEnabled() || !hasPermission(Perms.NoLimit.MOB_DAMAGE)) {
                    event.setCancelled(true);
                }
            }
        }
    }
    
    public void onChestAccess(PlayerInteractEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
            return;
        if (plugin.config.getPermissionsEnabled() && hasPermission(Perms.NoLimit.CHEST))
            return;
        event.getPlayer().sendMessage(L("blocked.chest"));
        event.setCancelled(true);
    }
    public void onChestAccess(PlayerInteractEntityEvent event) { // chest-minecarts are different events
        if (getPlayer().getGameMode() != GameMode.CREATIVE)
            return;
        if (plugin.config.getPermissionsEnabled() && hasPermission(Perms.NoLimit.CHEST))
            return;
        event.getPlayer().sendMessage(L("blocked.chest"));
        event.setCancelled(true);
    }
    public void onBenchAccess(PlayerInteractEvent event) {
        if (!plugin.config.getBenchBlock() || event.getPlayer().getGameMode() != GameMode.CREATIVE)
            return;
        if (plugin.config.getPermissionsEnabled() && hasPermission(Perms.NoLimit.CHEST))
            return;
        event.getPlayer().sendMessage(L("blocked.chest"));
        event.setCancelled(true);
    }
    public void onSignAccess(PlayerInteractEvent event) {
        if (!plugin.config.getSignBlock() || event.getPlayer().getGameMode() != GameMode.CREATIVE)
            return;
        if (plugin.config.getPermissionsEnabled() && hasPermission(Perms.NoLimit.SIGN))
            return;
        event.getPlayer().sendMessage(L("blocked.sign"));
        event.setCancelled(true);
    }
    public void onButtonAccess(PlayerInteractEvent event) {
        if (!plugin.config.getButtonBlock() || event.getPlayer().getGameMode() != GameMode.CREATIVE)
            return;
        if (event.getClickedBlock().getState() instanceof Lever) {
            if (plugin.config.getPermissionsEnabled() && hasPermission(Perms.NoLimit.LEVER))
                return;
            event.getPlayer().sendMessage(L("blocked.lever"));
            event.setCancelled(true);
        } else {
            if (plugin.config.getPermissionsEnabled() && hasPermission(Perms.NoLimit.BUTTON))
                return;
            event.getPlayer().sendMessage(L("blocked.button"));
            event.setCancelled(true);
        }
    }

    /*
     * Attention: "Creative" stands for "the other gamemode". So true may mean, "be survival in creative world".
     */
    public void setRegionCreativeAllowed(boolean rcreative, PlayerChangedAreaEvent changearea_event) {
        Core.debug(getName()+": changed region: "+rcreative+": "+changearea_event.toString());
        
        PlayerMoveEvent event = null;
        if (changearea_event != null)
            event = changearea_event.getMoveEvent();
        GameMode CURRENT_GAMEMODE = getPlayer().getGameMode();
        GameMode DEFAULT_GAMEMODE = plugin.com.getDefaultGameMode(event != null ? event.getTo().getWorld() : getPlayer().getWorld());
        GameMode TEMPORARY_GAMEMODE = DEFAULT_GAMEMODE == GameMode.SURVIVAL ? GameMode.CREATIVE : GameMode.SURVIVAL; // the opposite
        
        if (rcreative && CURRENT_GAMEMODE != TEMPORARY_GAMEMODE && !this.isRegionGameMode(TEMPORARY_GAMEMODE)) {
            Core.debug(getName()+": entering creative area");
            // 1. the region allows "the other (temporary) gamemode"
            // 2. but the player is not in that mode
            // 3. and the player is not aware of that
            // result: change him to that mode
            setRegionGameMode(TEMPORARY_GAMEMODE); // have to be set, before setGameMode
            
            boolean isOptional = changearea_event != null ?
                        isOptionalRegionGameMode(changearea_event.getNewRegionHash(), CURRENT_GAMEMODE) :
                        isOptionalRegionGameMode(CURRENT_GAMEMODE);
            if (!isOptional) {
                getPlayer().setGameMode(TEMPORARY_GAMEMODE);
            }
        } else if (!rcreative && getPlayer().getGameMode() == TEMPORARY_GAMEMODE && !isPermanentGameMode(TEMPORARY_GAMEMODE)) {
            Core.debug(getName()+": leaving creative area");
            // 1. the region doesn't allow "the other gamemode"
            // 2. but the player is in that mode
            // 3. and the player isn't global (permanent) in that mode
            // result: change him back to default mode
            if (event != null && CURRENT_GAMEMODE == GameMode.CREATIVE && getFloatingHeight() > plugin.config.getMaximumFloatingHeight()) {
                // but not if he is too high
                this.sendTimeoutMessage(L("blocked.survival_flying"));
                
                Location newloc = event.getTo().clone();
                newloc.setX(event.getFrom().getX());
                newloc.setY(event.getFrom().getY()); // well, otherwise flying high out of the region is possible
                newloc.setZ(event.getFrom().getZ());
                event.setTo(newloc);
            } else {
                setRegionGameMode(null);
                if (event != null && event.getTo().getWorld() == event.getFrom().getWorld()) {
                    // do not enforce the game mode change, on world teleport, as multiverse may cancel the event afterwards
                    // the world-change game-mode change is done by multiworld
                    event.getPlayer().setGameMode(DEFAULT_GAMEMODE);
                }
            }
        } else if (!rcreative && this.isRegionGameMode(TEMPORARY_GAMEMODE)) {
            Core.debug(getName()+": leaving creative area (while already in default gamemode)");
            // 1. the region doesn't allow "the other gamemode"
            // 2. but he thinks he is still allowed
            // 3. (because of else) we are not longer in that mode
            // result: advise him to not longer allowed to that region
            setRegionGameMode(null);
        }
    }

    private Map<String, Long> timeout_msgs = new HashMap<String, Long>();
    public void sendTimeoutMessage(String msg) {
        Iterator<Map.Entry<String, Long>> i = timeout_msgs.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, Long> entry = i.next();
            if (entry.getValue() < (System.currentTimeMillis() - plugin.config.getRepeatingMessageTimeout())) {
                i.remove();
            }
        }
        if (!timeout_msgs.containsKey(msg)) {
            timeout_msgs.put(msg, System.currentTimeMillis());
            getPlayer().sendMessage(msg);
        }
    }
    public int getFloatingHeight() {
        Block b = getPlayer().getLocation().getBlock();
        int steps = 0;
        while (b.getType() == Material.AIR) {
            steps++;
            b = b.getRelative(BlockFace.DOWN);
        }
        return steps;
    }
    
    public void goToFloor() {
        Block b = getPlayer().getLocation().getBlock();
        int steps = 0;
        while (b.getType() == Material.AIR) {
            steps++;
            b = b.getRelative(BlockFace.DOWN);
        }
        if (steps > 2) {
            getPlayer().teleport(new Location(getPlayer().getWorld(),
                    getPlayer().getLocation().getX(),
                    b.getY()+1,
                    getPlayer().getLocation().getZ()));
        }
    }
    
    public boolean hasPermission(IPermission permission) {
        return plugin.perm.hasPermission(this.getPlayer(), permission);
    }

    public boolean isGameModeAllowed(GameMode gm) {
        if (plugin.config.getRegionOptional() && isRegionGameMode()) {
            return true;
        }
        return false;
    }
}
