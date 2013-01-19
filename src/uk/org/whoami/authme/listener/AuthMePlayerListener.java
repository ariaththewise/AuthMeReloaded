/*
 * Copyright 2011 Sebastian Köhler <sebkoehler@whoami.org.uk>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.whoami.authme.listener;

import java.util.Date;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import uk.org.whoami.authme.api.API;
import uk.org.whoami.authme.cache.backup.DataFileCache;
import uk.org.whoami.authme.cache.backup.FileCache;
import uk.org.whoami.authme.AuthMe;
import uk.org.whoami.authme.ConsoleLogger;
import uk.org.whoami.authme.Utils;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.datasource.DataSource;
import uk.org.whoami.authme.events.ProtectInventoryEvent;
import uk.org.whoami.authme.events.RestoreInventoryEvent;
import uk.org.whoami.authme.events.SessionEvent;
import uk.org.whoami.authme.plugin.manager.CombatTagComunicator;
import uk.org.whoami.authme.settings.Messages;
import uk.org.whoami.authme.settings.PlayersLogs;
import uk.org.whoami.authme.settings.Settings;
import uk.org.whoami.authme.task.MessageTask;
import uk.org.whoami.authme.task.TimeoutTask;


public class AuthMePlayerListener implements Listener {

    
    public static int gm = 0;
    public static HashMap<String, Integer> gameMode = new HashMap<String, Integer>();
	private Utils utils = Utils.getInstance();
    private Messages m = Messages.getInstance();
    public AuthMe plugin;
    private DataSource data;
    private FileCache playerBackup = new FileCache();

    public AuthMePlayerListener(AuthMe plugin, DataSource data) {
        this.plugin = plugin;
        this.data = data;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        
        
        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player) ) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!Settings.isForcedRegistrationEnabled) {
                return;
            }
        }

        String msg = event.getMessage();
        //WorldEdit GUI Shit
        if (msg.equalsIgnoreCase("/worldedit cui")) {
            return;
        }

        String cmd = msg.split(" ")[0];
        if (cmd.equalsIgnoreCase("/login") || cmd.equalsIgnoreCase("/register") || cmd.equalsIgnoreCase("/passpartu") || cmd.equalsIgnoreCase("/l") || cmd.equalsIgnoreCase("/reg")) {
            return;
        }
        
        if (Settings.allowCommands.contains(cmd)) {
        	return;
        }

        event.setMessage("/notloggedin");
        event.setCancelled(true);
    }
    
    @EventHandler( priority = EventPriority.NORMAL)
    public void onPlayerNormalChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        
        final Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }
        
        String cmd = event.getMessage().split(" ")[0];

        if (!Settings.isChatAllowed && !(Settings.allowCommands.contains(cmd))) {
            //System.out.println("debug chat: chat isnt allowed");
            event.setCancelled(true);
            return;
        }
        
        if (!event.isAsynchronous()) {
        	if (data.isAuthAvailable(name)) {
        		player.sendMessage(m._("login_msg"));
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		player.sendMessage(m._("reg_msg"));
        	}
        } else {
        	if (data.isAuthAvailable(name)) {
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("login_msg"));
        			}
        		});
        		
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("reg_msg"));
        			}
        		});
        			
        	}
        }

                
    }
    
    @EventHandler( priority = EventPriority.HIGH)
    public void onPlayerHighChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        
        
        final Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }
        
        String cmd = event.getMessage().split(" ")[0];

        if (!Settings.isChatAllowed && !(Settings.allowCommands.contains(cmd))) {
            //System.out.println("debug chat: chat isnt allowed");
            event.setCancelled(true);
            return;
        }
        
        if (!event.isAsynchronous()) {
        	if (data.isAuthAvailable(name)) {
        		player.sendMessage(m._("login_msg"));
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		player.sendMessage(m._("reg_msg"));
        	}
        } else {
        	if (data.isAuthAvailable(name)) {
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("login_msg"));
        			}
        		});
        		
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("reg_msg"));
        			}
        		});
        		
        	}
        }

                
    }
    
    @EventHandler( priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        
        
        final Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }
        
        String cmd = event.getMessage().split(" ")[0];

        if (!Settings.isChatAllowed && !(Settings.allowCommands.contains(cmd))) {
            //System.out.println("debug chat: chat isnt allowed");
            event.setCancelled(true);
            return;
        }
        
        if (!event.isAsynchronous()) {
        	if (data.isAuthAvailable(name)) {
        		player.sendMessage(m._("login_msg"));
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		player.sendMessage(m._("reg_msg"));
        	}
        } else {
        	if (data.isAuthAvailable(name)) {
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("login_msg"));
        			}
        		});
        		
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("reg_msg"));
        			}
        		});
        		
        	}
        }

                
    }
    
    @EventHandler( priority = EventPriority.HIGHEST)
    public void onPlayerHighestChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        
        
        final Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }
        
        String cmd = event.getMessage().split(" ")[0];

        if (!Settings.isChatAllowed && !(Settings.allowCommands.contains(cmd))) {
            //System.out.println("debug chat: chat isnt allowed");
            event.setCancelled(true);
            return;
        }
        
        if (!event.isAsynchronous()) {
        	if (data.isAuthAvailable(name)) {
        		player.sendMessage(m._("login_msg"));
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		player.sendMessage(m._("reg_msg"));
        	}
        } else {
        	if (data.isAuthAvailable(name)) {
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("login_msg"));
        			}
        		});
        		
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("reg_msg"));
        			}
        		});
        		
        	}
        }

                
    }
    
    
    @EventHandler( priority = EventPriority.LOWEST)
    public void onPlayerEarlyChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        
        
        final Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }
        
        String cmd = event.getMessage().split(" ")[0];

        if (!Settings.isChatAllowed && !(Settings.allowCommands.contains(cmd))) {
            //System.out.println("debug chat: chat isnt allowed");
            event.setCancelled(true);
            return;
        }
        
        if (!event.isAsynchronous()) {
        	if (data.isAuthAvailable(name)) {
        		player.sendMessage(m._("login_msg"));
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		player.sendMessage(m._("reg_msg"));
        	}
        } else {
        	if (data.isAuthAvailable(name)) {
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("login_msg"));
        			}
        		});
        		
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("reg_msg"));
        			}
        		});
        		
        	}
        }

                
    }

    @EventHandler( priority = EventPriority.LOW)
    public void onPlayerLowChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        
        
        final Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }
        
        String cmd = event.getMessage().split(" ")[0];

        if (!Settings.isChatAllowed && !(Settings.allowCommands.contains(cmd))) {
            //System.out.println("debug chat: chat isnt allowed");
            event.setCancelled(true);
            return;
        }
        
        if (!event.isAsynchronous()) {
        	if (data.isAuthAvailable(name)) {
        		player.sendMessage(m._("login_msg"));
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		player.sendMessage(m._("reg_msg"));
        	}
        } else {
        	if (data.isAuthAvailable(name)) {
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("login_msg"));
        			}
        		});
        		
        	} else {
        		if (!Settings.isForcedRegistrationEnabled) {
        			return;
        		}
        		Bukkit.getScheduler().runTask(plugin, new Runnable()
        		{
        			@Override
        			public void run() {
        				player.sendMessage(m._("reg_msg"));
        			}
        		});
        		
        	}
        }

                
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (data.isAuthAvailable(name)) {
            event.setTo(event.getFrom());
            //event.setCancelled(true);
            return;
        }

        if (!Settings.isForcedRegistrationEnabled) {
            return;
        }

        if (!Settings.isMovementAllowed) {
            event.setTo(event.getFrom());
            //event.setCancelled(true);
            return;
        }

        if (Settings.getMovementRadius == 0) {
            return;
        }

        int radius = Settings.getMovementRadius;
        Location spawn = player.getWorld().getSpawnLocation();
        //Location to = event.getTo();
        
        if ((spawn.distance(player.getLocation()) > radius) ) {
            event.setTo(spawn);
        }
        /* old method
        if (to.getX() > spawn.getX() + radius || to.getX() < spawn.getX() - radius ||
            to.getY() > spawn.getY() + radius || to.getY() < spawn.getY() - radius ||
            to.getZ() > spawn.getZ() + radius || to.getZ() < spawn.getZ() - radius) {
            event.setTo(event.getFrom());
        } */
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
    	if (event.getResult() != Result.ALLOWED) {
    		return;
    	}
    	
        final Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
       
        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        //
        // If this check will fail mean that some permissions bypass kick, so player has to be
        // Switched on nonloggedIn group and try another time this kick!!
        //
        if(player.isOnline() && Settings.isForceSingleSessionEnabled ) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, m._("same_nick"));
            return;
        }
        
        if(data.isAuthAvailable(name) && !LimboCache.getInstance().hasLimboPlayer(name)) {
            if(!Settings.isSessionsEnabled) {
            LimboCache.getInstance().addLimboPlayer(player , utils.removeAll(player));
            } else if(PlayerCache.getInstance().isAuthenticated(name)) {
                if(LimboCache.getInstance().hasLimboPlayer(player.getName().toLowerCase())) {
                        LimboCache.getInstance().deleteLimboPlayer(name);  
                    }
                LimboCache.getInstance().addLimboPlayer(player , utils.removeAll(player));
            }
        }
        
        //Check if forceSingleSession is set to true, so kick player that has joined with same nick of online player
        if(player.isOnline() && Settings.isForceSingleSessionEnabled ) {
             LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(player.getName().toLowerCase()); 
             //System.out.println(" limbo ? "+limbo.getGroup());
             event.disallow(PlayerLoginEvent.Result.KICK_OTHER, m._("same_nick"));
                    if(PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
                        utils.addNormal(player, limbo.getGroup());
                        LimboCache.getInstance().deleteLimboPlayer(player.getName().toLowerCase());
                    }            
            return;
               
        }
        
        int min = Settings.getMinNickLength;
        int max = Settings.getMaxNickLength;
        String regex = Settings.getNickRegex;

        if (name.length() > max || name.length() < min) {

            event.disallow(Result.KICK_OTHER, m._("name_len"));
            return;
        }
        if (!player.getName().matches(regex) || name.equals("Player")) {
            try {
            	event.disallow(Result.KICK_OTHER, m._("regex").replaceAll("REG_EX", regex));
            } catch (StringIndexOutOfBoundsException exc) {
            	event.disallow(Result.KICK_OTHER, "allowed char : " + regex);
            }
            return;
        }
 
        if (Settings.isKickNonRegisteredEnabled) {
            if (!data.isAuthAvailable(name)) {    
                event.disallow(Result.KICK_OTHER, m._("reg_only"));
                return;
            }
        }
    }
    

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        gm = player.getGameMode().getValue();
        String name = player.getName().toLowerCase();
        gameMode.put(name, gm);
        BukkitScheduler sched = plugin.getServer().getScheduler();
        final PlayerJoinEvent e = event;

       
        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        String ip = player.getAddress().getAddress().getHostAddress();
            if(Settings.isAllowRestrictedIp && !Settings.getRestrictedIp(name, ip)) {
                int gM = gameMode.get(name);
            	player.setGameMode(GameMode.getByValue(gM));
                player.kickPlayer("You are not the Owner of this account, please try another name!");
                return;           
                }         
        
        if (data.isAuthAvailable(name)) {    
       
            
            if (Settings.isSessionsEnabled) {
                PlayerAuth auth = data.getAuth(name);
                long timeout = Settings.getSessionTimeout * 60000;
                long lastLogin = auth.getLastLogin();
                long cur = new Date().getTime();


             if((cur - lastLogin < timeout || timeout == 0) && !auth.getIp().equals("198.18.0.1") ) {
                if (auth.getNickname().equalsIgnoreCase(name) && auth.getIp().equals(ip) ) {
                	SessionEvent sessionevent = new SessionEvent(auth, true);
                	Bukkit.getServer().getPluginManager().callEvent(sessionevent);
                	if (!sessionevent.isCancelled() && sessionevent.isLogin()) {
                        PlayerCache.getInstance().addPlayer(auth);
                        player.sendMessage(m._("valid_session"));
                        return;
                	}
                } else {
                    int gM = gameMode.get(name);
                	player.setGameMode(GameMode.getByValue(gM));
                    player.kickPlayer(m._("unvalid_session"));
                    return;
                }
            } else {

                PlayerCache.getInstance().removePlayer(name);
                LimboCache.getInstance().addLimboPlayer(player , utils.removeAll(player));
                }
          } 
          // isent in session or session was ended correctly
          LimboCache.getInstance().addLimboPlayer(player);
          DataFileCache playerData = new DataFileCache(LimboCache.getInstance().getLimboPlayer(name).getInventory(),LimboCache.getInstance().getLimboPlayer(name).getArmour());      
          playerBackup.createCache(name, playerData, LimboCache.getInstance().getLimboPlayer(name).getGroup(),LimboCache.getInstance().getLimboPlayer(name).getOperator());                      
        } else {  
            if(!Settings.unRegisteredGroup.isEmpty()){
               utils.setGroup(player, Utils.groupType.UNREGISTERED);
            }
            if (!Settings.isForcedRegistrationEnabled) {
                return;
            }
        }



        if(Settings.protectInventoryBeforeLogInEnabled) {
        	try {
        		LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(player.getName().toLowerCase());
            	ProtectInventoryEvent ev = new ProtectInventoryEvent(player, limbo.getInventory(), limbo.getArmour(), 36, 4);
            	Bukkit.getServer().getPluginManager().callEvent(ev);
            	if (ev.isCancelled()) {
            		if (!Settings.noConsoleSpam)
            		ConsoleLogger.showError("ProtectInventoryEvent has been cancelled for " + player.getName() + " ...");
            	}
            		
        	} catch (NullPointerException ex) {

        	}

        }
 
        if(player.isOp()) 
            player.setOp(false);

        if (Settings.isTeleportToSpawnEnabled || Settings.isForceSpawnLocOnJoinEnabled) {
        	if (!player.getWorld().getChunkAt(player.getWorld().getSpawnLocation()).isLoaded()) {
        		player.getWorld().getChunkAt(player.getWorld().getSpawnLocation()).load();
        	}
            player.teleport(player.getWorld().getSpawnLocation());  
        }
        

        String msg = data.isAuthAvailable(name) ? m._("login_msg") : m._("reg_msg");
        int time = Settings.getRegistrationTimeout * 20;
        int msgInterval = Settings.getWarnMessageInterval;
        if (time != 0) {

            BukkitTask id = sched.runTaskLater(plugin, new TimeoutTask(plugin, name), time);
            if(!LimboCache.getInstance().hasLimboPlayer(name))
                 LimboCache.getInstance().addLimboPlayer(player);
            
            LimboCache.getInstance().getLimboPlayer(name).setTimeoutTaskId(id.getTaskId());
        }
        sched.runTask(plugin, new MessageTask(plugin, name, msg, msgInterval));
        
        if (Settings.isForceSurvivalModeEnabled)
        	sched.runTask(plugin, new Runnable() {
        		public void run() {
        			e.getPlayer().setGameMode(GameMode.SURVIVAL);
        		}
        	});
    }
    

	@EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == null) {
            return;
        }
        
        
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        
        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }
    
    if (PlayerCache.getInstance().isAuthenticated(name) && !player.isDead()) { 
        if(Settings.isSaveQuitLocationEnabled && data.isAuthAvailable(name)) {
            PlayerAuth auth = new PlayerAuth(event.getPlayer().getName().toLowerCase(),(int)player.getLocation().getX(),(int)player.getLocation().getY(),(int)player.getLocation().getZ());
            data.updateQuitLoc(auth);
        }
    } 
        
        if (LimboCache.getInstance().hasLimboPlayer(name)) {
            //System.out.println("e' nel quit");
            LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
            if(Settings.protectInventoryBeforeLogInEnabled && player.hasPlayedBefore()) {
            	RestoreInventoryEvent ev = new RestoreInventoryEvent(player, limbo.getInventory(), limbo.getArmour());
            	Bukkit.getServer().getPluginManager().callEvent(ev);
            	if (!ev.isCancelled()) {
            		API.setPlayerInventory(player, limbo.getInventory(), limbo.getArmour());
            	}
            }
            utils.addNormal(player, limbo.getGroup());
            player.setOp(limbo.getOperator());
            //System.out.println("debug quit group reset "+limbo.getGroup());
            this.plugin.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
            LimboCache.getInstance().deleteLimboPlayer(name);
            if(playerBackup.doesCacheExist(name)) {
                        playerBackup.removeCache(name);
            }
        }
        PlayerCache.getInstance().removePlayer(name);
        try {
        	PlayersLogs.players.remove(player.getName());
        	PlayersLogs.getInstance().save();
        } catch (NullPointerException ex) {
        	
        }
        if (gameMode.containsKey(name)) gameMode.remove(name);
        player.saveData();
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
      if (event.getPlayer() == null) {
        return;
      }

      Player player = event.getPlayer();

      if ((plugin.getCitizensCommunicator().isNPC(player, plugin)) || (Utils.getInstance().isUnrestricted(player)) || (CombatTagComunicator.isNPC(player))) {
        return;
      }

      if ((Settings.isForceSingleSessionEnabled.booleanValue()) && 
        (event.getReason().equals("You logged in from another location"))) {
        event.setCancelled(true);
        return;
      }

      String name = player.getName().toLowerCase();
      if ((PlayerCache.getInstance().isAuthenticated(name)) && (!player.isDead()) && 
        (Settings.isSaveQuitLocationEnabled.booleanValue())  && data.isAuthAvailable(name)) {
        PlayerAuth auth = new PlayerAuth(event.getPlayer().getName().toLowerCase(), (int)player.getLocation().getX(), (int)player.getLocation().getY(), (int)player.getLocation().getZ());
        this.data.updateQuitLoc(auth);
      }

      if (LimboCache.getInstance().hasLimboPlayer(name))
      {
        LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
        if (Settings.protectInventoryBeforeLogInEnabled.booleanValue()) {
        	RestoreInventoryEvent ev = new RestoreInventoryEvent(player, limbo.getInventory(), limbo.getArmour());
        	Bukkit.getServer().getPluginManager().callEvent(ev);
        	if (!ev.isCancelled()) {
        		API.setPlayerInventory(player, limbo.getInventory(), limbo.getArmour());
        	}
        }
        if (!limbo.getLoc().getWorld().getChunkAt(limbo.getLoc()).isLoaded()) {
        	limbo.getLoc().getWorld().getChunkAt(limbo.getLoc()).load();
        }
        player.teleport(limbo.getLoc());
        this.utils.addNormal(player, limbo.getGroup());
        player.setOp(limbo.getOperator());

        this.plugin.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
        LimboCache.getInstance().deleteLimboPlayer(name);
        if (this.playerBackup.doesCacheExist(name)) {
          this.playerBackup.removeCache(name);
        }
      }

      PlayerCache.getInstance().removePlayer(name);
      try {
          PlayersLogs.players.remove(player.getName());
    	  PlayersLogs.getInstance().save();
      } catch (NullPointerException ex) {}
      if (gameMode.containsKey(name)) gameMode.remove(name);
      player.saveData();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!Settings.isForcedRegistrationEnabled) {
                return;
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!Settings.isForcedRegistrationEnabled) {
                return;
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!Settings.isForcedRegistrationEnabled) {
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!Settings.isForcedRegistrationEnabled) {
                return;
            }
        }
        //System.out.println("player try to drop item");
        
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (plugin.getCitizensCommunicator().isNPC(player, plugin) || Utils.getInstance().isUnrestricted(player) || CombatTagComunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!Settings.isForcedRegistrationEnabled) {
                return;
            }
        }
        event.setCancelled(true);
    }
    
}
