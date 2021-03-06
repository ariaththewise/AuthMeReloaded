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

package uk.org.whoami.authme;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import uk.org.whoami.authme.api.API;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.backup.FileCache;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.commands.AdminCommand;
import uk.org.whoami.authme.commands.ChangePasswordCommand;
import uk.org.whoami.authme.commands.EmailCommand;
import uk.org.whoami.authme.commands.LoginCommand;
import uk.org.whoami.authme.commands.LogoutCommand;
import uk.org.whoami.authme.commands.RegisterCommand;
import uk.org.whoami.authme.commands.UnregisterCommand;
import uk.org.whoami.authme.datasource.CacheDataSource;
import uk.org.whoami.authme.datasource.DataSource;
import uk.org.whoami.authme.datasource.FileDataSource;
import uk.org.whoami.authme.datasource.MiniConnectionPoolManager.TimeoutException;
import uk.org.whoami.authme.datasource.MySQLDataSource;
import uk.org.whoami.authme.listener.AuthMeBlockListener;
import uk.org.whoami.authme.listener.AuthMeEntityListener;
import uk.org.whoami.authme.listener.AuthMePlayerListener;
import uk.org.whoami.authme.listener.AuthMeSpoutListener;
import uk.org.whoami.authme.plugin.manager.CitizensCommunicator;
import uk.org.whoami.authme.plugin.manager.CombatTagComunicator;
import uk.org.whoami.authme.settings.Messages;
import uk.org.whoami.authme.settings.PlayersLogs;
import uk.org.whoami.authme.settings.Settings;

import net.citizensnpcs.Citizens;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import uk.org.whoami.authme.commands.PasspartuCommand;
import uk.org.whoami.authme.datasource.SqliteDataSource;
import uk.org.whoami.authme.filter.ConsoleFilter;

public class AuthMe extends JavaPlugin {

    public DataSource database = null;
    private Settings settings;
	private Messages m;
    private PlayersLogs pllog;
	public Management management;
    public static Server server;
    public static Plugin authme;
    public static Permission permission;
	private static AuthMe instance;
    private Utils utils = Utils.getInstance();
    private JavaPlugin plugin;
    private FileCache playerBackup = new FileCache();
	public CitizensCommunicator citizens;
	public SendMailSSL mail = null;
	public int CitizensVersion = 0;
	public int CombatTag = 0;
	public API api;

    
    @Override
    public void onEnable() {
    	instance = this;
    	authme = instance;
    	
    	citizens = new CitizensCommunicator(this);

        settings = new Settings(this);
        settings.loadConfigOptions();
        
        setMessages(Messages.getInstance());
        pllog = PlayersLogs.getInstance();
        
        server = getServer();
        
        //Set Console Filter
        if (Settings.removePassword)
        Bukkit.getLogger().setFilter(new ConsoleFilter());
        
        
        //Load MailApi
		File mailFile = new File("lib", "mail.jar");
		if (mailFile.exists()) {
	        //Set SMTP
			mail = new SendMailSSL(this);
		} else {
			mail = null;
		}
		
		//Check Citizens Version
		citizensVersion();
        
		//Check Combat Tag Version
		combatTag();
        /*
         *  Back style on start if avaible
         */
        if(Settings.isBackupActivated && Settings.isBackupOnStart) {
        Boolean Backup = new PerformBackup(this).DoBackup();
        if(Backup) ConsoleLogger.info("Backup Complete");
            else ConsoleLogger.showError("Error while making Backup");
        }
        
        /*
         * Backend MYSQL - FILE - SQLITE
         */
        switch (Settings.getDataSource) {
            case FILE:
                try {
                    database = new FileDataSource();
                } catch (IOException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.showError("Can't use FLAT FILE... SHUTDOWN...");
                    	server.shutdown();
                    } 
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                break;
            case MYSQL:
                try {
                    database = new MySQLDataSource();
                } catch (ClassNotFoundException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.showError("Can't use MySQL... Please input correct MySQL informations ! SHUTDOWN...");
                    	server.shutdown();
                    } 
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                } catch (SQLException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.showError("Can't use MySQL... Please input correct MySQL informations ! SHUTDOWN...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                } catch(TimeoutException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.showError("Can't use MySQL... Please input correct MySQL informations ! SHUTDOWN...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                break;
            case SQLITE:
                try {
                     database = new SqliteDataSource();
                } catch (ClassNotFoundException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.showError("Can't use SQLITE... ! SHUTDOWN...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                } catch (SQLException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.showError("Can't use SQLITE... ! SHUTDOWN...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                break;
        }

        if (Settings.isCachingEnabled) {
            database = new CacheDataSource(database);
        }
        
    	api = new API(this, database);
        
        management =  new Management(database);
        
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new AuthMePlayerListener(this,database),this);
        pm.registerEvents(new AuthMeBlockListener(database, this),this);
        pm.registerEvents(new AuthMeEntityListener(database, this),this);
        if (pm.isPluginEnabled("Spout")) 
        	pm.registerEvents(new AuthMeSpoutListener(database),this);
        
        //Find Permissions
        if(Settings.isPermissionCheckEnabled) {
        RegisteredServiceProvider<Permission> permissionProvider =
                getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null)
            permission = permissionProvider.getProvider();
        else {
            
            ConsoleLogger.showError("Vault and Permissions plugins is needed for enable AuthMe Reloaded!");
            this.getServer().getPluginManager().disablePlugin(this);   
            }
        }
   
        this.getCommand("authme").setExecutor(new AdminCommand(this, database));
        this.getCommand("register").setExecutor(new RegisterCommand(database));
        this.getCommand("login").setExecutor(new LoginCommand());
        this.getCommand("changepassword").setExecutor(new ChangePasswordCommand(database));
        this.getCommand("logout").setExecutor(new LogoutCommand(this,database));
        this.getCommand("unregister").setExecutor(new UnregisterCommand(this, database));
        this.getCommand("passpartu").setExecutor(new PasspartuCommand(database));
        this.getCommand("email").setExecutor(new EmailCommand(this, database));
        
        if(!Settings.isForceSingleSessionEnabled) {
            ConsoleLogger.info("ATTENTION by disabling ForceSingleSession Your server protection is set to low");
        }
        
        if (Settings.reloadSupport)
        	try {
                if (!new File(getDataFolder() + File.separator + "players.yml").exists()) {
                	pllog = new PlayersLogs();
                }
                onReload();
                if (server.getOnlinePlayers().length < 1) {
                	try {
                    	PlayersLogs.players.clear();
                    	pllog.save();
                	} catch (NullPointerException npe) {
                	}
                }
        	} catch (NullPointerException ex) {
        		
        	}
        
        ConsoleLogger.info("Authme " + this.getDescription().getVersion() + " enabled");
    }

    private void combatTag() {
		if (this.getServer().getPluginManager().getPlugin("CombatTag") != null) {
			this.CombatTag = 1;
		} else {
			this.CombatTag = 0;
		}
	}

	private void citizensVersion() {
		if (this.getServer().getPluginManager().getPlugin("Citizens") != null) {
			Citizens cit = (Citizens) this.getServer().getPluginManager().getPlugin("Citizens");
            String ver = cit.getDescription().getVersion();
            String[] args = ver.split("\\.");
            if (args[0].contains("1")) {
            	this.CitizensVersion = 1;
            } else {
            	this.CitizensVersion = 2;
            }
		} else {
			this.CitizensVersion = 0;
		}
	}

	@Override
    public void onDisable() {
        if (Bukkit.getOnlinePlayers() != null)
        for(Player player : Bukkit.getOnlinePlayers()) {
        		this.savePlayer(player);
        }
        pllog.save();
        
        if (database != null) {
            database.close();
        }
        //utils = Utils.getInstance();
        
        /*
         *  Back style on start if avaible
         */
        if(Settings.isBackupActivated && Settings.isBackupOnStop) {
        Boolean Backup = new PerformBackup(this).DoBackup();
        if(Backup) ConsoleLogger.info("Backup Complete");
            else ConsoleLogger.showError("Error while making Backup");
        }       
        ConsoleLogger.info("Authme " + this.getDescription().getVersion() + " disabled");
        
        
    }

	private void onReload() {
		try {
	    	if (Bukkit.getServer().getOnlinePlayers() != null && !PlayersLogs.players.isEmpty()) {
	    		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
	    			if (PlayersLogs.players.contains(player.getName())) {
	    				String name = player.getName().toLowerCase();
	    		        PlayerAuth pAuth = database.getAuth(name);
	    	            // if Mysql is unavaible
	    	            if(pAuth == null)
	    	                break;
	    	            PlayerAuth auth = new PlayerAuth(name, pAuth.getHash(), pAuth.getIp(), new Date().getTime());
	    	            database.updateSession(auth);
	    				PlayerCache.getInstance().addPlayer(auth); 
	    			}
	    		}
	    	}
	    	return;
		} catch (NullPointerException ex) {
			return;
		}
    }
    
	public static AuthMe getInstance() {
		return instance;
	}
	
	public void savePlayer(Player player) throws IllegalStateException, NullPointerException {
		try {
	      if ((citizens.isNPC(player, this)) || (Utils.getInstance().isUnrestricted(player)) || (CombatTagComunicator.isNPC(player))) {
	          return;
	        }
		} catch (Exception e) { }
		
		try {
	        String name = player.getName().toLowerCase();
	        if ((PlayerCache.getInstance().isAuthenticated(name)) && (!player.isDead()) && 
	          (Settings.isSaveQuitLocationEnabled.booleanValue())) {
	          PlayerAuth auth = new PlayerAuth(player.getName().toLowerCase(), (int)player.getLocation().getX(), (int)player.getLocation().getY(), (int)player.getLocation().getZ());
	          this.database.updateQuitLoc(auth);
	        }

	        if (LimboCache.getInstance().hasLimboPlayer(name))
	        {
	          LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
	          if (Settings.protectInventoryBeforeLogInEnabled.booleanValue()) {
	            player.getInventory().setArmorContents(limbo.getArmour());
	            player.getInventory().setContents(limbo.getInventory());
	          }
	          if (!limbo.getLoc().getChunk().isLoaded()) {
	        	  limbo.getLoc().getChunk().load();
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
	        player.saveData();
	      } catch (Exception ex) {
	      }
	}

	public void setCitizensCommunicator(CitizensCommunicator citizens) {
		this.citizens = citizens;
	}

	public CitizensCommunicator getCitizensCommunicator() {
		return citizens;
	}

	public void setMessages(Messages m) {
		this.m = m;
	}

	public Messages getMessages() {
		return m;
	}
}
