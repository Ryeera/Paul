package de.Ryeera.PaulBot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.security.auth.login.LoginException;

import org.json.JSONArray;
import org.json.JSONObject;

import de.Ryeera.libs.DragoLogger;
import de.Ryeera.libs.JSONUtils;
import de.Ryeera.libs.SQLConnector;
import de.Ryeera.libs.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.GatewayPingEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

public class Paul extends ListenerAdapter{
	
	private static final String VERSION = "0.1";
	
	private static DragoLogger logger;
	
	private static File configFile = new File("paul.json");
	private static JSONObject config = new JSONObject();
	private static JDA jda;
	private static SQLConnector sql;

	public static void main(String[] args) {
		try{
			new File("logs").mkdirs();
			logger = new DragoLogger(new File("logs" + File.separator + "Paul_" + Utils.formatTime(System.currentTimeMillis(), "yyyyMMdd_HHmmss") + ".log"));
		}catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		logger.log("INFO", "Starting Paul v" + VERSION + "...");
		logger.log("INFO", "Setting up Configuration...");
		try {
			config = JSONUtils.readJSON(configFile);
		} catch (IOException e) {
			logger.log("SEVERE", "Couldn't read config-file! Halting...");
			logger.logStackTrace(e);
			return;
		}
		logger.log("INFO", "Setting up SQL-Connection...");
		JSONObject db = config.getJSONObject("database");
		sql = new SQLConnector(db.getString("address"), db.getInt("port"), db.getString("username"), db.getString("password"), db.getString("database"));
		sql.executeUpdate("CREATE TABLE IF NOT EXISTS `ActiveMatchrooms` ( `Category` BIGINT UNSIGNED NOT NULL , `Creator` BIGINT UNSIGNED NOT NULL , `Created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP , PRIMARY KEY (`Category`));");
		logger.log("INFO", "Setting up Discord-Connection...");
		JDABuilder builder = JDABuilder.createDefault(config.getString("token"));
		builder.enableIntents(GatewayIntent.GUILD_MEMBERS);
		builder.setActivity(Activity.watching("Bill play Beat Saber"));
		builder.setBulkDeleteSplittingEnabled(false);
		builder.addEventListeners(new Paul());
		try {
			jda = builder.build();
			jda.awaitReady();
			logger.log("INFO", "Bot started!");
		} catch (LoginException | InterruptedException e) {
			e.printStackTrace();
		}
		Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
			logger.log("INFO", "Checking tournament participants...");
			try {
				String currentTourney = config.optString("tourney");
				if (currentTourney.length() < 2) return;
				JSONObject tournament = new HTTPSUtils(new URL("https://beatkhana.com/api/tournament/" + currentTourney)).getJSONArray().getJSONObject(0);
				JSONArray participants = new HTTPSUtils(new URL("https://beatkhana.com/api/tournament/" + currentTourney + "/participants")).getJSONArray();
				boolean isLive = tournament.getString("state").equals("main_stage");
				boolean isArchived = tournament.getString("state").equals("archived");
				Guild guild = jda.getGuildById(config.getString("guild"));
				String proleid = config.getJSONObject("roles").getString("participant");
				Role prole = guild.getRoleById(proleid);
				String broleid = config.getJSONObject("roles").getString("backup");
				Role brole = guild.getRoleById(broleid);
				for(int i = 0; i < participants.length(); i++) {
					String userid = participants.getJSONObject(i).getString("discordId");
					final boolean isBackup = participants.getJSONObject(i).getInt("seed") == 0;
					final boolean isPlaced = participants.getJSONObject(i).getInt("position") > 0;
					final String pronouns = participants.getJSONObject(i).getString("pronoun");
					final String pronoun1 = pronouns.substring(0, pronouns.indexOf('/'));
					final String pnstate = pronoun1 + (pronoun1.equals("they") ? " are" : " is");
					guild.retrieveMemberById(userid).queue(member -> {
						if (isArchived) {
							if (member.getRoles().contains(prole)) {
								guild.removeRoleFromMember(userid, prole).queue(e -> {
									jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@&" + proleid + "> has been removed from <@" + userid + "> because the tourney is over!").queue();
									logger.log("INFO", "Removed participant-role to " + userid);
								});
							}
							if (member.getRoles().contains(brole)) {
								guild.removeRoleFromMember(userid, brole).queue(e -> {
									jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@&" + broleid + "> has been removed from <@" + userid + "> because the tourney is over!").queue();
									logger.log("INFO", "Removed backup-role from " + userid);
								});
							}
						} else if (isLive) {
							if (isPlaced) {
								if (member.getRoles().contains(prole)) {
									guild.removeRoleFromMember(userid, prole).queue(e -> {
										jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@&" + proleid + "> has been removed from <@" + userid + "> because " + pnstate.toLowerCase() + " out of the tourney!").queue();
										logger.log("INFO", "Removed participant-role to " + userid);
									});
								}
								if (member.getRoles().contains(brole)) {
									guild.removeRoleFromMember(userid, brole).queue(e -> {
										jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@&" + broleid + "> has been removed from <@" + userid + "> because " + pnstate.toLowerCase() + " out of the tourney!").queue();
										logger.log("INFO", "Removed backup-role from " + userid);
									});
								}
							} else if (isBackup) {
								if (!member.getRoles().contains(brole)) {
									guild.addRoleToMember(userid, brole).queue(e -> {
										jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@" + userid + "> has been given the role <@&" + broleid + ">").queue();
										logger.log("INFO", "Added backup-role to " + userid);
									});
								}
								if (member.getRoles().contains(prole)) {
									guild.removeRoleFromMember(userid, prole).queue(e -> {
										jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@&" + proleid + "> has been removed from <@" + userid + ">").queue();
										logger.log("INFO", "Removed participant-role from " + userid);
									});
								}
							} else {
								if (!member.getRoles().contains(prole)) {
									guild.addRoleToMember(userid, prole).queue(e -> {
										jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@" + userid + "> has been given the role <@&" + proleid + ">").queue();
										logger.log("INFO", "Added participant-role to " + userid);
									});
								}
								if (member.getRoles().contains(brole)) {
									guild.removeRoleFromMember(userid, brole).queue(e -> {
										jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@&" + broleid + "> has been removed from <@" + userid + ">").queue();
										logger.log("INFO", "Removed backup-role from " + userid);
									});
								}
							}
						} else {
							if (!member.getRoles().contains(prole)) {
								guild.addRoleToMember(userid, prole).queue(e -> {
									jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@" + userid + "> has been given the role <@&" + proleid + ">").queue();
									logger.log("INFO", "Added participant-role to " + userid);
								});
							}
						}
					}, f -> {
						boolean contains = false;
						for (Message m : jda.getTextChannelById(config.getString("log-channel")).getHistory().getRetrievedHistory()) {
							if (m.getContentRaw().equals("<@" + userid + "> **is not on this server!**")) {
								contains = true;
								break;
							}
						}
						if (contains)
							jda.getTextChannelById(config.getString("log-channel")).sendMessage("<@" + userid + "> **is not on this server!**").queue();
						logger.log("WARNING", "Couldn't find " + userid + " on the server!");
					});
				}
				if (isArchived) {
					config.put("tourney", "");
					JSONUtils.writeJSON(config, configFile);
				}
			} catch (IOException e) {
				logger.logStackTrace(e);
			}
			logger.log("INFO", "Done checking tournament participants!");
		}, 1, 30, TimeUnit.MINUTES);
	}
	
	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		Guild guild = event.getGuild();
		TextChannel channel = event.getChannel();
		Member member = event.getMember();
		String message = event.getMessage().getContentDisplay().toLowerCase();
		if (message.startsWith(config.getString("prefix"))) {
			message = message.substring(config.getString("prefix").length());
			if (message.startsWith("matchroom ")) {
				message = message.substring(10);
				JSONObject permissions = config.getJSONObject("permissions").getJSONObject("matchroom");
				List<Role> readRoles = new ArrayList<>();
				for(int i = 0; i < permissions.getJSONArray("read").length(); i++) {
					readRoles.add(jda.getRoleById(permissions.getJSONArray("read").getString(i)));
				}
				List<Role> writeRoles = new ArrayList<>();
				for(int i = 0; i < permissions.getJSONArray("write").length(); i++) {
					writeRoles.add(jda.getRoleById(permissions.getJSONArray("write").getString(i)));
				}
				List<Role> createRoles = new ArrayList<>();
				boolean hasCreatePermission = false;
				for(int i = 0; i < permissions.getJSONArray("create").length(); i++) {
					Role role = jda.getRoleById(permissions.getJSONArray("create").getString(i));
					createRoles.add(role);
					if(member.getRoles().contains(role)) {
						hasCreatePermission = true;
						break;
					}
				}
				List<Role> adminRoles = new ArrayList<>();
				boolean hasAdminPermission = false;
				for(int i = 0; i < permissions.getJSONArray("admin").length(); i++) {
					Role role = jda.getRoleById(permissions.getJSONArray("admin").getString(i));
					adminRoles.add(role);
					if(member.getRoles().contains(role)) {
						hasAdminPermission = true;
						hasCreatePermission = true;
						break;
					}
				}
				try {
					ResultSet matchroom = sql.executeQuery("SELECT * FROM `ActiveMatchrooms` WHERE `Category`=" + channel.getParent().getId());
					boolean isMatchroom = matchroom.first();
					if (message.startsWith("create")) {
						message = message.substring(7);
						if (!hasCreatePermission) {
							channel.sendMessage("You are missing a role with the permission `matchroom.create`!").queue();
							return;
						}
						if(!message.contains("@")) {
							channel.sendMessage("You need to mention at least one member! `" + config.getString("prefix") + "matchroom create [Room-Name] [@Mention] <@Mention...>`").queue();
							return;
						}
						if(message.trim().startsWith("@")) {
							channel.sendMessage("You didn't specify a name for the matchroom! `" + config.getString("prefix") + "matchroom create [Room-Name] [@Mention] <@Mention...>`").queue();
							return;
						}
						String channelname = message.substring(0, message.indexOf(" @"));
						ChannelAction<Category> action = guild.createCategory(channelname);
						action.addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(
								Permission.MANAGE_PERMISSIONS, 
								Permission.VIEW_CHANNEL, 
								Permission.MESSAGE_WRITE, 
								Permission.MESSAGE_MANAGE, 
								Permission.VOICE_CONNECT,
								Permission.VOICE_SPEAK,
								Permission.VOICE_MUTE_OTHERS, 
								Permission.VOICE_DEAF_OTHERS, 
								Permission.VOICE_MOVE_OTHERS, 
								Permission.PRIORITY_SPEAKER));
						for(Role role : readRoles) {
							action.addPermissionOverride(role, EnumSet.of(
									Permission.VIEW_CHANNEL, 
									Permission.VOICE_CONNECT), null);
						}
						for(Role role : writeRoles) {
							action.addPermissionOverride(role, EnumSet.of(
									Permission.VIEW_CHANNEL, 
									Permission.MESSAGE_WRITE, 
									Permission.VOICE_SPEAK), null);
						}
						for(Role role : adminRoles) {
							action.addPermissionOverride(role, EnumSet.of(
									Permission.MANAGE_PERMISSIONS, 
									Permission.VIEW_CHANNEL, 
									Permission.MESSAGE_WRITE, 
									Permission.MESSAGE_MANAGE, 
									Permission.VOICE_CONNECT, 
									Permission.VOICE_SPEAK,
									Permission.VOICE_MUTE_OTHERS, 
									Permission.VOICE_DEAF_OTHERS, 
									Permission.VOICE_MOVE_OTHERS, 
									Permission.PRIORITY_SPEAKER), null);
						}
						for(Member player : event.getMessage().getMentionedMembers()) {
							action.addPermissionOverride(player, EnumSet.of(
									Permission.VIEW_CHANNEL, 
									Permission.MESSAGE_WRITE, 
									Permission.VOICE_CONNECT,
									Permission.VOICE_SPEAK), null);
						}
						action.addPermissionOverride(member, EnumSet.of(
								Permission.VIEW_CHANNEL, 
								Permission.MESSAGE_WRITE, 
								Permission.MESSAGE_MANAGE,
								Permission.VOICE_CONNECT,
								Permission.VOICE_SPEAK,
								Permission.VOICE_MUTE_OTHERS, 
								Permission.VOICE_DEAF_OTHERS, 
								Permission.VOICE_MOVE_OTHERS,
								Permission.PRIORITY_SPEAKER
								), null);
						action.queue(c -> {
							c.createVoiceChannel(channelname).queue(vc -> {
								try {
									guild.moveVoiceMember(member, vc).queue();
								} catch (IllegalStateException ex) {}
								for(Member player : event.getMessage().getMentionedMembers()) {
									try {
										guild.moveVoiceMember(player, vc).queue();
									} catch (IllegalStateException ex) {}
								}
							});
							c.createTextChannel(channelname).queue(tc -> {
								MessageBuilder mb = new MessageBuilder();
								mb.append("This is the matchroom managed by ");
								mb.append(member);
								mb.append(".\n The players in this matchroom are:\n");
								for(Member player : event.getMessage().getMentionedMembers()) {
									mb.append(player);
									mb.append("\n");
								}
								mb.append("**Please connect to the Voice-channel to get started!**");
								tc.sendMessage(mb.build()).queue();
								jda.getTextChannelById(config.getString("log-channel")).sendMessage(member.getAsMention() + " has created the matchroom `" + channelname + "`").queue();
							});
							sql.executeUpdate("INSERT INTO `ActiveMatchrooms` (`Category`, `Creator`) VALUES ('" + c.getId() + "', '" + member.getId() + "')");
						});
					} else if (message.equals("close")) {
						if (!isMatchroom) {
							channel.sendMessage("You need to be in an active matchroom to close it!").queue();
							return;
						}
						if(!(matchroom.getLong("Creator") == member.getIdLong()) && !hasAdminPermission) {
							channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
							return;
						}
						channel.sendMessage("Closing matchroom...").queue(e -> {
							try {
								Category cat = guild.getCategoryById(matchroom.getLong("Category"));
								for(Role role : readRoles) {
									cat.getPermissionOverride(role).delete().queue();
								}
								for(Role role : writeRoles) {
									cat.getPermissionOverride(role).delete().queue();
								}
								for(PermissionOverride override : cat.getMemberPermissionOverrides()) {
									override.delete().queue();
								}
								for(Member members : cat.getVoiceChannels().get(0).getMembers()) {
									try {
										guild.moveVoiceMember(members, guild.getVoiceChannelById(config.getString("default-vc"))).queue();
									} catch (IllegalStateException ex) {}
								}
								sql.executeUpdate("DELETE FROM `ActiveMatchrooms` WHERE `Category` = " + cat.getId());
								jda.getTextChannelById(config.getString("log-channel")).sendMessage(member.getAsMention() + " has closed the matchroom `" + cat.getName() + "`").queue();
							} catch (SQLException e1) {}
						});
					} else if (message.equals("delete")) {
						if (!isMatchroom) {
							channel.sendMessage("You need to be in an active matchroom to delete it!").queue();
							return;
						}
						if (!hasAdminPermission) {
							channel.sendMessage("You are missing a role with the permission `matchroom.admin`!").queue();
							return;
						}
						channel.sendMessage("Deleting Matchroom...").queue(e -> {
							try {
								Category cat = guild.getCategoryById(matchroom.getLong("Category"));
								for(Member members : cat.getVoiceChannels().get(0).getMembers()) {
									try {
										guild.moveVoiceMember(members, guild.getVoiceChannelById(config.getString("default-vc"))).queue();
									} catch (IllegalStateException ex) {}
								}
								sql.executeUpdate("DELETE FROM `ActiveMatchrooms` WHERE `Category` = " + cat.getId());
								for(GuildChannel gc : cat.getChannels()) {
									gc.delete().queueAfter(10, TimeUnit.SECONDS);
								}
								cat.delete().queueAfter(10, TimeUnit.SECONDS);
								jda.getTextChannelById(config.getString("log-channel")).sendMessage(member.getAsMention() + " has deleted the matchroom `" + cat.getName() + "`").queue();
							} catch (SQLException e1) {}
						});
					} else if (message.startsWith("addmember")) {
						if (!isMatchroom) {
							channel.sendMessage("You need to be in an active matchroom to add a member!").queue();
							return;
						}
						if(!(matchroom.getLong("Creator") == member.getIdLong()) && !hasAdminPermission) {
							channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
							return;
						}
						if(event.getMessage().getMentionedMembers().size() != 1) {
							channel.sendMessage("You need to mention exactly one member! `" + config.getString("prefix") + "matchroom addmember [@Mention]`!").queue();
							return;
						}
						Category cat = guild.getCategoryById(matchroom.getLong("Category"));
						cat.createPermissionOverride(event.getMessage().getMentionedMembers().get(0)).grant(
								Permission.VIEW_CHANNEL, 
								Permission.MESSAGE_WRITE, 
								Permission.VOICE_CONNECT,
								Permission.VOICE_SPEAK
						).queue(e -> {
							MessageBuilder mb = new MessageBuilder();
							mb.append(event.getMessage().getMentionedMembers().get(0));
							mb.append(" has been added to this matchroom!");
							channel.sendMessage(mb.build()).queue();
							try {
								guild.moveVoiceMember(event.getMessage().getMentionedMembers().get(0), cat.getVoiceChannels().get(0)).queue();
							} catch (IllegalStateException ex) {}
						});
					} else if (message.startsWith("removemember")) {
						if (!isMatchroom) {
							channel.sendMessage("You need to be in an active matchroom to remove a member!").queue();
							return;
						}
						if(!(matchroom.getLong("Creator") == member.getIdLong()) && !hasAdminPermission) {
							channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
							return;
						}
						if(event.getMessage().getMentionedMembers().size() != 1) {
							channel.sendMessage("You need to mention exactly one member! `" + config.getString("prefix") + "matchroom removemember [@Mention]`!").queue();
							return;
						}
						Category cat = guild.getCategoryById(matchroom.getLong("Category"));
						cat.getPermissionOverride(event.getMessage().getMentionedMembers().get(0)).delete().queue();
						MessageBuilder mb = new MessageBuilder();
						mb.append(event.getMessage().getMentionedMembers().get(0));
						mb.append(" has been removed from this matchroom!");
						channel.sendMessage(mb.build()).queue();
						if (cat.getVoiceChannels().get(0).getMembers().contains(event.getMessage().getMentionedMembers().get(0))) {
							try {
								guild.moveVoiceMember(event.getMessage().getMentionedMembers().get(0), guild.getVoiceChannelById(config.getString("default-vc"))).queue();
							} catch (IllegalStateException e) {}
						}
					} else if (message.startsWith("mute")) {
						if (!isMatchroom) {
							channel.sendMessage("You need to be in an active matchroom to mute everyone!").queue();
							return;
						}
						if(!(matchroom.getLong("Creator") == member.getIdLong()) && !hasAdminPermission) {
							channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
							return;
						}
						Category cat = guild.getCategoryById(matchroom.getLong("Category"));
						for(VoiceChannel vc : cat.getVoiceChannels()) {
							for(Member mem : vc.getMembers()) {
								mem.mute(true).queue();
							}
						}
					} else if (message.startsWith("unmute")) {
						if (!isMatchroom) {
							channel.sendMessage("You need to be in an active matchroom to unmute everyone!").queue();
							return;
						}
						if(!(matchroom.getLong("Creator") == member.getIdLong()) && !hasAdminPermission) {
							channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
							return;
						}
						Category cat = guild.getCategoryById(matchroom.getLong("Category"));
						for(VoiceChannel vc : cat.getVoiceChannels()) {
							for(Member mem : vc.getMembers()) {
								mem.mute(false).queue();
							}
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} else if (message.startsWith("settourney ")) {
				message = message.substring(11);
				config.put("tourney", message);
				try {
					JSONUtils.writeJSON(config, configFile);
				} catch (FileNotFoundException e) {
					channel.sendMessage("Warning: Config could not be saved! Tell Ryeera he fucked up.").queue();
				}
				channel.sendMessage("The current tourney is now https://beatkhana.com/tournament/" + message).queue();
			}
		}
	}
	
	@Override
	public void onGatewayPing(GatewayPingEvent event) {
		for(Guild guild : jda.getGuilds()) {
			guild.getSelfMember().modifyNickname(config.getString("nickname")).queue();
		}
	}
}
