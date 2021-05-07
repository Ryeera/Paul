package de.Ryeera.PaulBot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.security.auth.login.LoginException;

import org.json.JSONArray;
import org.json.JSONObject;

import de.Ryeera.libs.DragoLogger;
import de.Ryeera.libs.JSONUtils;
import de.Ryeera.libs.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
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
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.GatewayPingEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class Paul extends ListenerAdapter{
	
	private static final String VERSION = "0.3.2";
	
	private static DragoLogger logger;
	
	private static File configFile = new File("paul.json");
	private static File matchroomFile = new File("matchrooms.json");
	private static JSONObject config = new JSONObject();
	private static JSONObject matchrooms = new JSONObject();
	private static JDA jda;
	
	private static List<Role> adminRoles = new ArrayList<>();
	private static List<Role> createRoles = new ArrayList<>();
	private static List<Role> writeRoles = new ArrayList<>();
	private static List<Role> readRoles = new ArrayList<>();

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
			System.exit(-1);
		}
		
		logger.log("INFO", "Setting up active Matchrooms...");
		if (!matchroomFile.exists()) {
			try {
				JSONUtils.writeJSON(matchrooms, matchroomFile);
			} catch (FileNotFoundException e) {
				logger.log("ERROR", "Couldn't create matchroom-file! Halting...");
				logger.logStackTrace(e);
				System.exit(-2);
			}
		}
		try {
			matchrooms = JSONUtils.readJSON(matchroomFile);
		} catch (IOException e) {
			logger.log("ERROR", "Couldn't read matchroom-file! Halting...");
			logger.logStackTrace(e);
			System.exit(-3);
		}
		
		logger.log("INFO", "Setting up Discord-Connection...");
		JDABuilder builder = JDABuilder.create(config.getString("token"), EnumSet.allOf(GatewayIntent.class));
		builder.enableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOTE, CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
		builder.setActivity(Activity.watching("Bill play Beat Saber"));
		builder.setBulkDeleteSplittingEnabled(false);
		builder.addEventListeners(new Paul());
		try {
			jda = builder.build();
			jda.awaitReady();
			for (Guild guild : jda.getGuilds()) {
				guild.loadMembers().onSuccess(m -> {
					logger.log("INFO", "Loaded " + m.size() + " members for " + guild.getName());
				});
			}
			logger.log("INFO", "Bot started!");
		} catch (LoginException | InterruptedException e) {
			e.printStackTrace();
		}
		
		logger.log("INFO", "Setting up matchroom-roles...");
		JSONObject permissions = config.getJSONObject("permissions").getJSONObject("matchroom");
		readRoles = new ArrayList<>();
		for(int i = 0; i < permissions.getJSONArray("read").length(); i++) {
			readRoles.add(jda.getRoleById(permissions.getJSONArray("read").getString(i)));
		}
		writeRoles = new ArrayList<>();
		for(int i = 0; i < permissions.getJSONArray("write").length(); i++) {
			writeRoles.add(jda.getRoleById(permissions.getJSONArray("write").getString(i)));
		}
		createRoles = new ArrayList<>();
		for(int i = 0; i < permissions.getJSONArray("create").length(); i++) {
			Role role = jda.getRoleById(permissions.getJSONArray("create").getString(i));
			createRoles.add(role);
		}
		adminRoles = new ArrayList<>();
		for(int i = 0; i < permissions.getJSONArray("admin").length(); i++) {
			Role role = jda.getRoleById(permissions.getJSONArray("admin").getString(i));
			adminRoles.add(role);
		}
		
		logger.log("INFO", "Setting up Participant-Checker...");
		Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
			try {
				logger.log("INFO", "Checking tournament participants...");
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
					final String pronoun1 = (pronouns.contains("He") || pronouns.contains("he") ? "He" : (pronouns.contains("She") || pronouns.contains("she") ? "She" : "They"));
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
				logger.log("INFO", "Done checking tournament participants!");
			} catch (Exception e) {
				logger.log("ERROR", "Error while checking participants!");
				logger.logStackTrace(e);
			}
		}, 30, 30, TimeUnit.MINUTES);
		logger.log("INFO", "Paul started!");
	}
	
	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		Guild guild = event.getGuild();
		TextChannel channel = event.getChannel();
		Member member = event.getMember();
		String message = event.getMessage().getContentRaw();
		if (message.startsWith(config.getString("prefix"))) {
			message = message.substring(config.getString("prefix").length());
			String [] args = message.split(" ");
			if (args[0].equalsIgnoreCase("matchroom") || args[0].equalsIgnoreCase("mr")) {
				boolean hasCreatePermission = false;
				for(Role role : createRoles) {
					if(member.getRoles().contains(role)) {
						hasCreatePermission = true;
						break;
					}
				}
				boolean hasAdminPermission = false;
				for(Role role : adminRoles) {
					if(member.getRoles().contains(role)) {
						hasAdminPermission = true;
						hasCreatePermission = true;
						break;
					}
				}
				MatchRoom matchroom = getMatchroom(channel.getParent().getId());
				boolean isMatchroom = matchroom != null;
				if (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("make")) {
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
					String channelname = args[2];
					int nextid = 3;
					for (int i = 3; i < args.length; i++) {
						if (args[i].startsWith("<@!")) {
							nextid = i;
							break;
						}
						try {
							if (guild.getMemberById(args[i]) != null) {
								nextid = i;
								break;
							} else {
								channelname += " " + args[i];
							}
						} catch (NumberFormatException e) {
							channelname += " " + args[i];
						}
					}
					ChannelAction<Category> action = guild.createCategory(channelname);
					action.addPermissionOverride(guild.getPublicRole(), EnumSet.of(Permission.VIEW_CHANNEL), EnumSet.of(
							Permission.MANAGE_PERMISSIONS, 
							Permission.MESSAGE_HISTORY,
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
								Permission.MESSAGE_HISTORY,
								Permission.VOICE_CONNECT), null);
					}
					for(Role role : writeRoles) {
						action.addPermissionOverride(role, EnumSet.of(
								Permission.VIEW_CHANNEL, 
								Permission.MESSAGE_HISTORY,
								Permission.MESSAGE_WRITE, 
								Permission.VOICE_SPEAK), null);
					}
					for(Role role : adminRoles) {
						action.addPermissionOverride(role, EnumSet.of(
								Permission.MANAGE_PERMISSIONS, 
								Permission.VIEW_CHANNEL, 
								Permission.MESSAGE_HISTORY,
								Permission.MESSAGE_WRITE, 
								Permission.MESSAGE_MANAGE, 
								Permission.VOICE_CONNECT, 
								Permission.VOICE_SPEAK,
								Permission.VOICE_MUTE_OTHERS, 
								Permission.VOICE_DEAF_OTHERS, 
								Permission.VOICE_MOVE_OTHERS, 
								Permission.PRIORITY_SPEAKER), null);
					}
					action.addPermissionOverride(member, EnumSet.of(
							Permission.VIEW_CHANNEL, 
							Permission.MESSAGE_HISTORY,
							Permission.MESSAGE_WRITE, 
							Permission.MESSAGE_MANAGE,
							Permission.VOICE_CONNECT,
							Permission.VOICE_SPEAK,
							Permission.VOICE_MUTE_OTHERS, 
							Permission.VOICE_DEAF_OTHERS, 
							Permission.VOICE_MOVE_OTHERS,
							Permission.PRIORITY_SPEAKER
							), null);
					for (int i = nextid; i < args.length; i++) {
						if (args[i].startsWith("<@&")) {
							continue;
						}
						if (args[i].startsWith("<@!")) {
							args[i] = args[i].substring(3, args[i].length()-1);
						}
						try {
							Member player = guild.getMemberById(args[i]);
							if (player != null) {
								action.addPermissionOverride(player, EnumSet.of(
										Permission.VIEW_CHANNEL, 
										Permission.MESSAGE_HISTORY,
										Permission.MESSAGE_WRITE, 
										Permission.VOICE_CONNECT,
										Permission.VOICE_SPEAK), null);
							}
						} catch (NumberFormatException e) {}
					}
					final String cname = channelname;
					action.queue(c -> {
						c.createVoiceChannel(cname).queue(vc -> {
							try {
								guild.moveVoiceMember(member, vc).queue();
							} catch (IllegalStateException ex) {}
							for(Member player : event.getMessage().getMentionedMembers()) {
								try {
									guild.moveVoiceMember(player, vc).queue();
								} catch (IllegalStateException ex) {}
							}
						});
						c.createTextChannel(cname).queue(tc -> {
							MessageBuilder mb = new MessageBuilder();
							mb.append("This is the matchroom managed by ");
							mb.append(member);
							mb.append(".\n The players in this matchroom are:\n");
							for(PermissionOverride or : tc.getMemberPermissionOverrides()) {
								mb.append(or.getMember().getAsMention());
								mb.append("\n");
							}
							mb.append("**Please connect to the Voice-channel to get started!**");
							tc.sendMessage(mb.build()).queue();
							jda.getTextChannelById(config.getString("log-channel")).sendMessage(member.getAsMention() + " has created the matchroom `" + cname + "`").queue();
						});
						addMatchroom(c.getId(), member);
						logger.log("INFO", member.getUser().getAsTag() + " created matchroom " + cname);
					});
				} else if (args[1].equalsIgnoreCase("close")) {
					if (!isMatchroom) {
						channel.sendMessage("You need to be in an active matchroom to close it!").queue();
						return;
					}
					if(!(matchroom.getCreatorID() == member.getIdLong()) && !hasAdminPermission) {
						channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
						return;
					}
					channel.sendMessage("Closing matchroom...").queue(e -> {
						Category cat = guild.getCategoryById(matchroom.getCatID());
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
						removeMatchroom(cat.getId());
						jda.getTextChannelById(config.getString("log-channel")).sendMessage(member.getAsMention() + " has closed the matchroom `" + cat.getName() + "`").queue();
					});
					logger.log("INFO", member.getUser().getAsTag() + " closed matchroom " + channel.getParent().getName());
				} else if (args[1].equalsIgnoreCase("delete")) {
					if (!isMatchroom) {
						channel.sendMessage("You need to be in an active matchroom to delete it!").queue();
						return;
					}
					if (!(matchroom.getCreatorID() == member.getIdLong()) && !hasAdminPermission) {
						channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
						return;
					}
					channel.sendMessage("Deleting Matchroom...").queue(e -> {
						Category cat = guild.getCategoryById(matchroom.getCatID());
						for(Member members : cat.getVoiceChannels().get(0).getMembers()) {
							try {
								guild.moveVoiceMember(members, guild.getVoiceChannelById(config.getString("default-vc"))).queue();
							} catch (IllegalStateException ex) {}
						}
						removeMatchroom(cat.getId());
						for(GuildChannel gc : cat.getChannels()) {
							gc.delete().queueAfter(10, TimeUnit.SECONDS);
						}
						cat.delete().queueAfter(15, TimeUnit.SECONDS);
						jda.getTextChannelById(config.getString("log-channel")).sendMessage(member.getAsMention() + " has deleted the matchroom `" + cat.getName() + "`").queue();
					});
					logger.log("INFO", member.getUser().getAsTag() + " deleted matchroom " + channel.getParent().getName());
				} else if (args[1].equalsIgnoreCase("addmember") || args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("am")) {
					if (!isMatchroom) {
						channel.sendMessage("You need to be in an active matchroom to add a member!").queue();
						return;
					}
					if(!(matchroom.getCreatorID() == member.getIdLong()) && !hasAdminPermission) {
						channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
						return;
					}
					if(event.getMessage().getMentionedMembers().size() != 1) {
						channel.sendMessage("You need to mention exactly one member! `" + config.getString("prefix") + "matchroom addmember [@Mention]`!").queue();
						return;
					}
					Category cat = guild.getCategoryById(matchroom.getCatID());
					for (int i = 2; i < args.length; i++) {
						if (args[i].startsWith("<@&")) {
							continue;
						}
						if (args[i].startsWith("<@!")) {
							args[i] = args[i].substring(3, args[i].length()-1);
						}
						try {
							Member player = guild.getMemberById(args[i]);
							if (player != null) {
								cat.putPermissionOverride(player).grant(
										Permission.VIEW_CHANNEL, 
										Permission.MESSAGE_HISTORY,
										Permission.MESSAGE_WRITE, 
										Permission.VOICE_CONNECT,
										Permission.VOICE_SPEAK).queue(e -> {
											MessageBuilder mb = new MessageBuilder();
											mb.append(player.getUser().getAsMention());
											mb.append(" has been added to this matchroom!");
											channel.sendMessage(mb.build()).queue();
											try {
												guild.moveVoiceMember(player, cat.getVoiceChannels().get(0)).queue();
											} catch (IllegalStateException ex) {}
										});
										logger.log("INFO", member.getUser().getAsTag() + " has added " + event.getMessage().getMentionedMembers().get(0).getUser().getAsTag() + " to matchroom " + channel.getParent().getName());
							}
						} catch (NumberFormatException e) {}
					}
				} else if (args[1].equalsIgnoreCase("removemember") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("rm")) {
					if (!isMatchroom) {
						channel.sendMessage("You need to be in an active matchroom to remove a member!").queue();
						return;
					}
					if(!(matchroom.getCreatorID() == member.getIdLong()) && !hasAdminPermission) {
						channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
						return;
					}
					if(event.getMessage().getMentionedMembers().size() != 1) {
						channel.sendMessage("You need to mention exactly one member! `" + config.getString("prefix") + "matchroom removemember [@Mention]`!").queue();
						return;
					}
					Category cat = guild.getCategoryById(matchroom.getCatID());
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
					logger.log("INFO", member.getUser().getAsTag() + " has removed " + event.getMessage().getMentionedMembers().get(0).getUser().getAsTag() + " from matchroom " + channel.getParent().getName());
				} else if (args[1].equalsIgnoreCase("mute") || args[1].equalsIgnoreCase("m")) {
					if (!isMatchroom) {
						channel.sendMessage("You need to be in an active matchroom to mute everyone!").queue();
						return;
					}
					if(!(matchroom.getCreatorID() == member.getIdLong()) && !hasAdminPermission) {
						channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
						return;
					}
					Category cat = guild.getCategoryById(matchroom.getCatID());
					for(VoiceChannel vc : cat.getVoiceChannels()) {
						for(Member mem : vc.getMembers()) {
							if (mem.equals(member)) continue;
							mem.mute(true).queue();
						}
					}
					logger.log("INFO", member.getUser().getAsTag() + " muted all members in matchroom " + channel.getParent().getName());
				} else if (args[1].equalsIgnoreCase("unmute") || args[1].equalsIgnoreCase("um")) {
					if (!isMatchroom) {
						channel.sendMessage("You need to be in an active matchroom to unmute everyone!").queue();
						return;
					}
					if(!(matchroom.getCreatorID() == member.getIdLong()) && !hasAdminPermission) {
						channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
						return;
					}
					Category cat = guild.getCategoryById(matchroom.getCatID());
					for(VoiceChannel vc : cat.getVoiceChannels()) {
						for(Member mem : vc.getMembers()) {
							if (mem.equals(member)) continue;
							mem.mute(false).queue();
						}
					}
					logger.log("INFO", member.getUser().getAsTag() + " unmuted all members in matchroom " + channel.getParent().getName());
				} else if (args[1].equalsIgnoreCase("clear")) {
					if (!isMatchroom) {
						channel.sendMessage("You need to be in an active matchroom to unmute everyone!").queue();
						return;
					}
					if(!(matchroom.getCreatorID() == member.getIdLong()) && !hasAdminPermission) {
						channel.sendMessage("This is not your matchroom and you are missing a role with the permission `matchroom.admin`!").queue();
						return;
					}
					Category cat = guild.getCategoryById(matchroom.getCatID());
					for (VoiceChannel vc : cat.getVoiceChannels()) {
						for (Member mem : vc.getMembers()) {
							if (mem.equals(member)) continue;
							guild.kickVoiceMember(mem).queue();
						}
					}
					for (PermissionOverride or : cat.getMemberPermissionOverrides()) {
						if (or.getMember().equals(member)) continue;
						or.delete().queue();
					}
				}
			} else if (args[0].equalsIgnoreCase("settourney") || args[0].equalsIgnoreCase("st")) {
				boolean hasAdminPermission = false;
				for(Role role : adminRoles) {
					if(member.getRoles().contains(role)) {
						hasAdminPermission = true;
						break;
					}
				}
				if (hasAdminPermission) {
					config.put("tourney", args[1]);
					try {
						JSONUtils.writeJSON(config, configFile);
					} catch (FileNotFoundException e) {
						channel.sendMessage("Warning: Config could not be saved! Tell Ryeera he fucked up.").queue();
					}
					channel.sendMessage("The current tourney is now https://beatkhana.com/tournament/" + args[1]).queue();
				}
			} else if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("h")) {
				channel.sendMessage(getHelpEmbed()).queue();
			}
		}
	}
	
	public static MessageEmbed getHelpEmbed() {
		EmbedBuilder eb = new EmbedBuilder();
		eb.setAuthor("Paul");
		eb.setColor(0xFF9566);
		eb.setFooter("I am watching...");
		eb.setThumbnail("https://cdn.discordapp.com/avatars/708347348148813894/cc00ea2a73578af34b7dcdff5f92be1e.png");
		eb.setTimestamp(Instant.now());
		eb.setTitle("Paul Commands");
		String prefix = config.getString("prefix").replace("<@!708347348148813894>", "@Paul ");
		eb.setDescription("Here are all the commands you can use:");
		eb.addField("__**Help**__", "**Usage:** `" + prefix + "help`\n**Description:** Shows this help-message.", false);
		eb.addField("__**Matchrooms**__", "**Usage:** `" + prefix + "mr [subcommand]`\n**Description:** See subcommands below.\n**Aliases:** `" + prefix + "mr`", false);
		eb.addField("__**Create Matchrooms**__", "**Usage:** `" + prefix + "mr create [name] [@mention] <@mention...>`\n**Description:** Creates a new matchroom with given names and anywhere from 1 to unlimited members that were pinged. Can also use the user-ID instead of pings.\n**Aliases:** `" + prefix + "mr make`", false);
		eb.addField("__**Add Member to Matchroom**__", "**Usage:** `" + prefix + "mr addmember [@mention] <@mention...>`\n**Description:** Adds anywhere from 1 to unlimited members that were pinged to the matchroom. Can also use the user-ID instead of pings. Command has to be used in a matchroom!\n**Aliases:** `" + prefix + "mr add`, `" + prefix + "mr am`", false);
		eb.addField("__**Remove Member from Matchroom**__", "**Usage** `" + prefix + "mr removemember [@mention] <@mention...>`\n**Description:** Removes anywhere from 1 to unlimited members that were pinged to the matchroom. Can also use the user-ID instead of pings. Command has to be used in a matchroom!\n**Aliases:** `" + prefix + "mr remove`, `" + prefix + "mr rm`", false);
		eb.addField("__**Mute Matchroom**__", "**Usage:** `" + prefix + "mr mute`\n**Description:** Mutes all players in the matchroom.\n**Aliases:** `" + prefix + "mr m`", false);
		eb.addField("__**Unmute Matchroom**__", "**Usage:** `" + prefix + "mr unmute`\n**Description:** Unmutes all players in the matchroom.\n**Aliases:** `" + prefix + "mr um`", false);
		eb.addField("__**Clear Matchroom**__", "**Usage:** `" + prefix + "mr clear`\n**Description:** Removes all players from the matchroom.", false);
		eb.addField("__**Close Matchrooms**__", "**Usage:** `" + prefix + "mr close`\n**Description:** Closes the matchroom, but doesn't delete it. Use this if not explicitly told to delete the matchroom!.", false);
		eb.addField("__**Delete Matchrooms**__", "**Usage:** `" + prefix + "mr delete`\n**Description:** Deletes the matchroom. Use this only if explicitly told to delete the matchroom!.", false);
		return eb.build();
	}
	
	@Override
	public void onGatewayPing(GatewayPingEvent event) {
		for(Guild guild : jda.getGuilds()) {
			guild.getSelfMember().modifyNickname(config.getString("nickname")).queue();
		}
	}
	
	public static boolean isMatchroom(String catID) {
		return matchrooms.has(catID);
	}
	
	public static boolean addMatchroom(String catID, Member creator) {
		JSONObject matchroom = new JSONObject();
		matchroom.put("creatorID", creator.getId());
		matchroom.put("creatorName", creator.getUser().getAsTag());
		matchroom.put("created", new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Date.from(Instant.now())));
		matchrooms.put(catID, matchroom);
		try {
			JSONUtils.writeJSON(matchrooms, matchroomFile);
			return true;
		} catch (FileNotFoundException e) {
			logger.log("ERROR", "Failed to add matchroom with ID " + catID + " to file!");
			logger.logStackTrace(e);
			return false;
		}
	}
	
	public static boolean removeMatchroom(String catID) {
		if (!isMatchroom(catID)) return false;
		matchrooms.remove(catID);
		try {
			JSONUtils.writeJSON(matchrooms, matchroomFile);
			return true;
		} catch (FileNotFoundException e) {
			logger.log("ERROR", "Failed to remove matchroom with ID " + catID + " from file!");
			logger.logStackTrace(e);
			return false;
		}
	}
	
	public static MatchRoom getMatchroom(String catID) {
		if (!isMatchroom(catID)) return null;
		JSONObject matchroom = matchrooms.getJSONObject(catID);
		return new MatchRoom (catID, matchroom.getLong("creatorID"), matchroom.getString("creatorName"), matchroom.getString("created"));
	}
}
