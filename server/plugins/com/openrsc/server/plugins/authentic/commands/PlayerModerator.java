package com.openrsc.server.plugins.authentic.commands;

import com.openrsc.server.constants.AppearanceId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.impl.mysql.queries.logging.StaffLog;
import com.openrsc.server.database.struct.LinkedPlayer;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.CommandTrigger;
import com.openrsc.server.util.rsc.AppearanceRetroConverter;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.openrsc.server.constants.AppearanceId.*;
import static com.openrsc.server.plugins.Functions.*;

public final class PlayerModerator implements CommandTrigger {
	private static final Logger LOGGER = LogManager.getLogger(PlayerModerator.class);

	public static String messagePrefix = null;
	public static String badSyntaxPrefix = null;

	public boolean blockCommand(Player player, String command, String[] args) {
		return player.isMod() || player.isPlayerMod();
	}

	@Override
	public void onCommand(Player player, String command, String[] args) {
		if(messagePrefix == null) {
			messagePrefix = config().MESSAGE_PREFIX;
		}
		if(badSyntaxPrefix == null) {
			badSyntaxPrefix = config().BAD_SYNTAX_PREFIX;
		}

		if (command.equalsIgnoreCase("gmute")) {
			mutePlayerGlobal(player, command, args);
		} else if (command.equalsIgnoreCase("ungmute")) {
			unmutePlayerGlobal(player, command, args);
		} else if (command.equalsIgnoreCase("mute")) {
			mutePlayer(player, command, args);
		} else if (command.equalsIgnoreCase("unmute")) {
			unmutePlayer(player, command, args);
		} else if (command.equalsIgnoreCase("alert")) {
			showPlayerAlertBox(player, command, args);
		} else if (command.equalsIgnoreCase("set_icon")) {
			setIcon(player, args);
		} else if (command.equalsIgnoreCase("redhat") || command.equalsIgnoreCase("rhel")) {
			setRedHat(player);
		} else if (command.equalsIgnoreCase("robe") || command.equalsIgnoreCase("setrobe") || command.equalsIgnoreCase("setrobes")) {
			setRobes(player, args);
		} else if (command.equalsIgnoreCase("becomeNpc") || command.equalsIgnoreCase("morph") || command.equalsIgnoreCase("morphNpc")) {
			becomeNpc(player, args);
		} else if (command.equalsIgnoreCase("becomegod")) {
			becomeGod(player);
		} else if (command.equalsIgnoreCase("speaktongues")) {
			speakTongues(player, 2);
		} else if (command.equalsIgnoreCase("restorehumanity") || command.equalsIgnoreCase("resetappearance")) {
			restoreHumanity(player, args);
		} else if (command.equalsIgnoreCase("become")) {
			if (args[0].equalsIgnoreCase("god")) {
				becomeGod(player);
			} else {
				becomeNpc(player, args);
			}
		} else if (command.equalsIgnoreCase("check")) {
			queryPlayerAlternateCharacters(player, command, args);
		}
	}

	private void queryPlayerAlternateCharacters(Player player, String command, String[] args) {
		if(args.length < 1) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [player]");
			return;
		}

		String targetUsername	= args[0];
		Player target			= player.getWorld().getPlayer(DataConversions.usernameToHash(targetUsername));

		String currentIp = null;
		if (target == null) {
			try {
				currentIp = player.getWorld().getServer().getDatabase().playerLoginIp(targetUsername);

				if(currentIp == null) {
					player.message(messagePrefix + "No character named '" + targetUsername + "' is online or was found in the database.");
					return;
				}
			} catch (final GameDatabaseException e) {
				LOGGER.catching(e);
				player.message(messagePrefix + "A Database error has occurred! " + e.getMessage());
				return;
			}
		} else {
			currentIp = target.getCurrentIP();
		}

		try {
			final LinkedPlayer[] linkedPlayers = player.getWorld().getServer().getDatabase().linkedPlayers(currentIp);

			// Check if any of the found users have a group less than the player who is running this command
			boolean authorized = true;
			for (final LinkedPlayer linkedPlayer : linkedPlayers) {
				if(linkedPlayer.groupId < player.getGroupID())
				{
					authorized = false;
					break;
				}
			}

			List<String> names = new ArrayList<>();
			for (final LinkedPlayer linkedPlayer : linkedPlayers) {
				String dbUsername	= linkedPlayer.username;
				// Only display usernames if the player running the action has a better rank or if the username is the one being targeted
				if(authorized || dbUsername.toLowerCase().trim().equals(targetUsername.toLowerCase().trim()))
					names.add(dbUsername);
			}
			StringBuilder builder = new StringBuilder("@red@")
				.append(targetUsername.toUpperCase());
			if (target != null) {
				builder.append(" (" + target.getX() + "," + target.getY() + ")");
			}
			builder.append(" @whi@currently has ")
				.append(names.size() > 0 ? "@gre@" : "@red@")
				.append(names.size())
				.append(" @whi@registered characters.");

			if(player.isAdmin())
				builder.append(" %IP Address: " + currentIp);

			if (names.size() > 0) {
				builder.append(" % % They are: ");
			}
			for (int i = 0; i < names.size(); i++) {

				builder.append("@yel@").append(player.getWorld().getPlayer(DataConversions.usernameToHash(names.get(i))) != null
					? "@gre@" : "@red@").append(names.get(i));

				if (i != names.size() - 1) {
					builder.append("@whi@, ");
				}
			}

			player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 18, target));
			ActionSender.sendBox(player, builder.toString(), names.size() > 10);
		} catch (final GameDatabaseException ex) {
			player.message(messagePrefix + "A MySQL error has occured! " + ex.getMessage());
		}
	}

	private void mute(final Player player, final Player targetPlayer, final int targetPlayerId,
					  final String targetPlayerUsername, final int duration, final boolean notify,
					  final String reason, final int muteType) {
		final String muteText = muteType == 0 ? " " : " global chat ";
		final String minuteText = duration == -1 ? "permanent" : duration + " minute";

		// Player offline mute
		if (targetPlayer == null) {
			try {
				player.getWorld().getServer().getDatabase().updatePlayerMute(targetPlayerId, duration, muteType);
			} catch (GameDatabaseException ex) {
				player.message(messagePrefix + "A database error has occurred.");
				LOGGER.catching(ex);
				return;
			}
		} else {
			if (duration == 0) {
				// Handle unmute
				if (!player.isInvisibleTo(targetPlayer)) {
					targetPlayer.message("Your " + muteText + "mute has been lifted. Happy Classic Scaping!");
				}

				if (muteType == 0) {
					targetPlayer.setMuteExpires(System.currentTimeMillis());
				} else {
					targetPlayer.setGlobalMuteExpires(System.currentTimeMillis());
				}
			} else {
				// Handle muting
				if (!player.isInvisibleTo(targetPlayer)) {
					targetPlayer.message(messagePrefix + "You have received a " + minuteText + muteText + "mute.");
				}

				final long endTime = duration == -1 ? -1 : System.currentTimeMillis() + (duration * 60000L);
				if (muteType == 0) {
					targetPlayer.setMuteExpires(endTime);
				} else {
					targetPlayer.setGlobalMuteExpires(endTime);
				}
				targetPlayer.setMuteNotify(notify);
			}
		}

		// Message the muter and log
		if (duration == 0) {
			// Unmute
			player.message(messagePrefix + "You have lifted the" + muteText + "mute of " + targetPlayerUsername + ".");

			player.getWorld().getServer().getGameLogger().addQuery(
				new StaffLog(player, 0, targetPlayer, targetPlayerUsername
					+ " had their " + muteText + "mute lifted."));
		} else {
			// Mute
			player.message(messagePrefix + "You have given " + targetPlayerUsername + " a " + minuteText + muteText + "mute.");

			player.getWorld().getServer().getGameLogger().addQuery(
				new StaffLog(player, 0, targetPlayer, targetPlayerUsername
					+ " was given a " + minuteText + muteText + "mute."
					+ (!reason.equals("") ? "Reason: " + reason : "")));
		}
	}

	private void unmutePlayerGlobal(Player player, String command, String[] args) {
		if (args.length < 1) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [name]");
			return;
		}
		mutePlayerGlobal(player, command, new String[]{ args[0], "0" });
	}

	private void mutePlayerGlobal(Player player, String command, String[] args) {
		if (args.length < 1) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [name] [time in minutes, -1 for permanent, 0 to unmute] ...");
			player.message("... (notify) (Reason)");
			return;
		}

		final Player targetPlayer = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
		int targetPlayerId = -1;
		String targetPlayerUsername = "";

		if (targetPlayer != null) {
			targetPlayerId = targetPlayer.getID();
			targetPlayerUsername = targetPlayer.getUsername();
			if (targetPlayer == player) {
				player.message(messagePrefix + "You can't mute or unmute yourself");
				return;
			}
			if (!targetPlayer.isDefaultUser() && player.getGroupID() >= targetPlayer.getGroupID()) {
				player.message(messagePrefix + "You can not mute a staff member of equal or greater rank.");
				return;
			}
		} else {
			// Get the targetPlayer's player ID since they aren't logged in
			targetPlayerUsername = args[0].replace('.',' ');
			try {
				targetPlayerId = player.getWorld().getServer().getDatabase().playerIdFromUsername(targetPlayerUsername);
				if (targetPlayerId == -1) {
					player.message(messagePrefix + "The player you have specified does not exist");
					return;
				}
			} catch (GameDatabaseException ex) {
				player.message(messagePrefix + "A database error has occurred.");
				LOGGER.catching(ex);
				return;
			}
		}

		int minutes = -1;
		if (args.length >= 2) {
			try {
				minutes = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " [name] [time in minutes, -1 for permanent, 0 to unmute] ...");
				player.message("... (notify) (Reason)");
				return;
			}
		} else {
			minutes = player.isSuperMod() ? -1 : player.isMod() ? 60 : 15;
		}

		if (!player.isSuperMod()) {
			if (minutes == 0) {
				player.message(messagePrefix + "You are not allowed to unmute users.");
				return;
			}
			if (minutes == -1) {
				player.message(messagePrefix + "You are not allowed to mute indefinitely.");
				return;
			}
		}

		if (!player.isMod() && minutes > 10080) {
			player.message(messagePrefix + "You are not allowed to mute that user for more than a week (10,080 minutes).");
			return;
		}

		boolean notify;
		if (args.length >= 3) {
			try {
				notify = Integer.parseInt(args[2]) == 1;
			} catch (NumberFormatException nfe) {
				notify = Boolean.parseBoolean(args[2]);
			}
		} else {
			notify = false;
		}

		String reason;
		if (args.length >= 4) {
			reason = args[3];
		} else {
			reason = "";
		}

		mute(player, targetPlayer, targetPlayerId, targetPlayerUsername, minutes, notify, reason, 1);
	}

	private void unmutePlayer(Player player, String command, String[] args) {
		if (args.length < 1) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [name]");
			return;
		}
		mutePlayer(player, command, new String[]{ args[0], "0" });
	}

	private void mutePlayer(Player player, String command, String[] args) {
		if (args.length < 1) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [name] [time in minutes, -1 for permanent, 0 to unmute] ...");
			player.message("... (notify) (Reason)");
			return;
		}

		final Player targetPlayer = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
		int targetPlayerId = -1;
		String targetPlayerUsername = "";

		if (targetPlayer != null) {
			targetPlayerId = targetPlayer.getID();
			targetPlayerUsername = targetPlayer.getUsername();
			if (targetPlayer == player) {
				player.message(messagePrefix + "You can't mute or unmute yourself");
				return;
			}
			if (!targetPlayer.isDefaultUser() && player.getGroupID() >= targetPlayer.getGroupID()) {
				player.message(messagePrefix + "You can not mute a staff member of equal or greater rank.");
				return;
			}
		} else {
			// Get the targetPlayer's player ID since they aren't logged in
			targetPlayerUsername = args[0].replace('.',' ');
			try {
				targetPlayerId = player.getWorld().getServer().getDatabase().playerIdFromUsername(targetPlayerUsername);
				if (targetPlayerId == -1) {
					player.message(messagePrefix + "The player you have specified does not exist");
					return;
				}
			} catch (GameDatabaseException ex) {
				player.message(messagePrefix + "A database error has occurred.");
				LOGGER.catching(ex);
				return;
			}
		}

		int minutes = -1;
		if (args.length >= 2) {
			try {
				minutes = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " [name] [time in minutes, -1 for permanent, 0 to unmute] ...");
				player.message("... (notify) (Reason)");
				return;
			}
		} else {
			minutes = 60;
		}

		if (!player.isMod() && minutes == -1) {
			player.message(messagePrefix + "You are not allowed to mute indefinitely.");
			return;
		}

		if (!player.isMod() && minutes > 10080) {
			player.message(messagePrefix + "You are not allowed to mute that user for more than a week (10,080 minutes).");
			return;
		}

		boolean notify;
		if (args.length >= 3) {
			try {
				notify = Integer.parseInt(args[2]) == 1;
			} catch (NumberFormatException nfe) {
				notify = Boolean.parseBoolean(args[2]);
			}
		} else {
			notify = false;
		}

		String reason;
		if (args.length >= 4) {
			reason = args[3];
		} else {
			reason = "";
		}

		mute(player, targetPlayer, targetPlayerId, targetPlayerUsername, minutes, notify, reason, 0);
	}

	private void showPlayerAlertBox(Player player, String command, String[] args) {
		StringBuilder message = new StringBuilder();
		if (args.length > 0) {
			Player targetPlayer = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));

			if (targetPlayer != null) {
				for (int i = 1; i < args.length; i++)
					message.append(args[i]).append(" ");
				if (targetPlayer.getClientLimitations().supportsMessageBox) {
					ActionSender.sendBox(targetPlayer, "@yel@Alert from a Moderator:%@whi@ " + message, false);
				}
				targetPlayer.playerServerMessage(MessageType.QUEST, "@gre@Moderator:@whi@ " + message);
				player.message(messagePrefix + "Alerted " + targetPlayer.getUsername());
			} else
				player.message(messagePrefix + "Invalid name or player is not online");
		} else
			player.message(badSyntaxPrefix + command.toUpperCase() + " [name] [message]");
	}

	private void setIcon(Player player, String[] args) {
		int icon = -1;
		try {
			icon = Integer.parseInt(args[0]);
		} catch (Exception e) {
			player.message("Could not parse integer.");
			player.message("Usage: @mag@::set_icon [integer]");
		}
		player.preferredIcon = icon;
	}

	private void setRedHat(Player player) {
		player.updateWornItems(ZAMORAK_WIZARDSHAT); // unobtainable zamorak hat sprite, used by gnomeish peoples
		player.updateWornItems(ZAMORAK_MONK_ROBE);
		player.updateWornItems(ZAMORAK_MONK_SKIRT);
		player.getUpdateFlags().setAppearanceChanged(true);
	}

	private void setRobes(Player player, String[] args) {
		if (args.length == 0) {
			mes("Usage: @mag@::setRobes [colour description] (username)");
		}

		Player affectedPlayer = player;
		if (args.length > 1) {
			if (player.isAdmin()) {
				affectedPlayer = player.getWorld().getPlayer(DataConversions.usernameToHash(args[1]));
				if (affectedPlayer == null) {
					mes("Couldn't find that player.");
					return;
				}
			} else {
				mes("Sorry, but only admins may bestow special robes for other players.");
				return;
			}
		}

		String colourName = args[0].trim().toLowerCase();
		switch (colourName) {
			case "red":
			case "zamorak":
			case "zammy":
				affectedPlayer.updateWornItems(ZAMORAK_WIZARDSHAT);
				affectedPlayer.updateWornItems(ZAMORAK_MONK_ROBE);
				affectedPlayer.updateWornItems(ZAMORAK_MONK_SKIRT);
				break;
			case "blue":
			case "wizard":
				affectedPlayer.updateWornItems(WIZARDSHAT);
				affectedPlayer.updateWornItems(WIZARDS_ROBE);
				affectedPlayer.updateWornItems(BLUE_SKIRT);

				break;
			case "darkwizard":
			case "blackwizard":
			case "grey":
			case "gray":
				affectedPlayer.updateWornItems(DARKWIZARDSHAT);
				affectedPlayer.updateWornItems(DARKWIZARDS_ROBE);
				affectedPlayer.updateWornItems(BLACK_SKIRT);
				break;

			case "monk":
			case "brown":
			case "sara":
			case "saradomin":
				affectedPlayer.updateWornItems(BALD_HEAD);
				affectedPlayer.updateWornItems(SARADOMIN_MONK_ROBE);
				affectedPlayer.updateWornItems(SARADOMIN_MONK_SKIRT);
				break;

			case "pink":
			case "gnomepink":
			case "gnomered":
				affectedPlayer.updateWornItems(PASTEL_PINK_GNOMESHAT);
				affectedPlayer.updateWornItems(PASTEL_PINK_GNOME_TOP);
				affectedPlayer.updateWornItems(PASTEL_PINK_GNOME_SKIRT);
				break;

			case "green":
			case "gnomegreen":
				affectedPlayer.updateWornItems(PASTEL_GREEN_GNOMESHAT);
				affectedPlayer.updateWornItems(PASTEL_GREEN_GNOME_TOP);
				affectedPlayer.updateWornItems(PASTEL_GREEN_GNOME_SKIRT);
				break;

			case "purple":
			case "gnomeblue":
			case "gnomepurple":
				affectedPlayer.updateWornItems(PASTEL_BLUE_GNOMESHAT);
				affectedPlayer.updateWornItems(PASTEL_BLUE_GNOME_TOP);
				affectedPlayer.updateWornItems(PASTEL_BLUE_GNOME_SKIRT);
				break;

			case "yellow":
			case "gnomeyellow":
			case "canary":
				affectedPlayer.updateWornItems(PASTEL_YELLOW_GNOMESHAT);
				affectedPlayer.updateWornItems(PASTEL_YELLOW_GNOME_TOP);
				affectedPlayer.updateWornItems(PASTEL_YELLOW_GNOME_SKIRT);
				break;

			case "gnomelightblue":
			case "gnomecyan":
			case "gnometurquoise":
			case "lightblue":
			case "turquoise":
			case "cyan":
				affectedPlayer.updateWornItems(PASTEL_CYAN_GNOMESHAT);
				affectedPlayer.updateWornItems(PASTEL_CYAN_GNOME_TOP);
				affectedPlayer.updateWornItems(PASTEL_CYAN_GNOME_SKIRT);
				break;

			case "fullwhite":
				affectedPlayer.updateWornItems(CHEFS_HAT); // the only white hat, other than armour
			case "white":
			case "guthix":
			case "druid":
				affectedPlayer.updateWornItems(DRUID_ROBE);
				affectedPlayer.updateWornItems(DRUID_SKIRT);
				break;

			case "pitchblack":
			case "black":
			case "shadowwarrior":
				affectedPlayer.updateWornItems(SHADOW_WARRIOR_ROBE);
				affectedPlayer.updateWornItems(SHADOW_WARRIOR_SKIRT);
				break;

			case "mourner":
				affectedPlayer.updateWornItems(GAS_MASK);
				affectedPlayer.updateWornItems(LEATHER_ARMOUR);
				affectedPlayer.updateWornItems(MOURNER_LEGS);
				break;

			case "disable":
			case "none":
			case "reset":
				restoreHumanity(affectedPlayer);
				break;
			default:
				mes("don't know that one, sorry.");
		}

		affectedPlayer.getUpdateFlags().setAppearanceChanged(true);
	}

	private void becomeNpc(Player player, String[] args) {
		if (args.length == 0) {
			mes("Usage: @mag@::becomeNpc [npc name] (position) (username)");
		}
		String npcName = args[0].trim().toLowerCase();
		int pos = AppearanceId.SLOT_NPC;
		if (args.length > 1) {
			try {
				pos = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				switch (args[1].trim().toLowerCase()) {
					case "head":
						pos = AppearanceId.SLOT_HEAD;
						break;
					case "shirt":
						pos = AppearanceId.SLOT_SHIRT;
						break;
					case "pants":
						pos = AppearanceId.SLOT_PANTS;
						break;
					case "shield":
						pos = AppearanceId.SLOT_SHIELD;
						break;
					case "weapon":
						pos = AppearanceId.SLOT_WEAPON;
						break;
					case "hat":
						pos = AppearanceId.SLOT_HAT;
						break;
					case "body":
						pos = AppearanceId.SLOT_BODY;
						break;
					case "legs":
						pos = AppearanceId.SLOT_LEGS;
						break;
					case "gloves":
						pos = AppearanceId.SLOT_GLOVES;
						break;
					case "boots":
						pos = AppearanceId.SLOT_BOOTS;
						break;
					case "amulet":
						pos = AppearanceId.SLOT_AMULET;
						break;
					case "cape":
						pos = AppearanceId.SLOT_CAPE;
						break;
				}
			}
		}

		Player affectedPlayer = player;
		if (args.length > 2) {
			if (player.isAdmin()) {
				affectedPlayer = player.getWorld().getPlayer(DataConversions.usernameToHash(args[2]));
				if (affectedPlayer == null) {
					mes("Couldn't find that player.");
					return;
				}
			} else {
				mes("Sorry, but only admins may bestow NPC appearances for other players.");
				return;
			}
		}

		switch (npcName) {
			case "rat":
				updateAppearanceToNpc(affectedPlayer, RAT, pos);
				break;
			case "demon":
			case "greaterdemon":
			case "lesserdemon":
			case "imp":
				updateAppearanceToNpc(affectedPlayer, DEMON, pos);
				break;
			case "spider":
				updateAppearanceToNpc(affectedPlayer, SPIDER, pos);
				break;
			case "redspider":
				updateAppearanceToNpc(affectedPlayer, RED_SPIDER, pos);
				break;
			case "camel":
				updateAppearanceToNpc(affectedPlayer, CAMEL, pos);
				break;
			case "cow":
				updateAppearanceToNpc(affectedPlayer, COW, pos);
				break;
			case "sheep":
			case "chomp":
			case "bheep":
				updateAppearanceToNpc(affectedPlayer, SHEEP, pos); // I think the only NPC without fighting animations
				break;
			case "unicorn":
				updateAppearanceToNpc(affectedPlayer, UNICORN, pos);
				break;
			case "bear":
				updateAppearanceToNpc(affectedPlayer, BEAR, pos);
				break;
			case "chicken":
				updateAppearanceToNpc(affectedPlayer, CHICKEN, pos);
				break;
			case "armedskeleton":
				player.updateWornItems(SKELETON_SCIMITAR_AND_SHIELD);
			case "skeleton":
				updateAppearanceToNpc(affectedPlayer, SKELETON, pos);
				break;
			case "armedzombie":
				player.updateWornItems(ZOMBIE_AXE);
			case "zombie":
				updateAppearanceToNpc(affectedPlayer, ZOMBIE, pos);
				break;
			case "ghost":
				updateAppearanceToNpc(affectedPlayer, GHOST, pos);
				break;
			case "bat":
				updateAppearanceToNpc(affectedPlayer, BAT, pos);
				break;
			case "armedgoblin":
				player.updateWornItems(GOBLIN_SPEAR);
			case "goblin":
				updateAppearanceToNpc(affectedPlayer, GOBLIN, pos);
				break;
			case "redgoblin":
				updateAppearanceToNpc(affectedPlayer, GOBLIN_WITH_RED_ARMOUR, pos);
				break;
			case "greengoblin":
				updateAppearanceToNpc(affectedPlayer, GOBLIN_WITH_GREEN_ARMOUR, pos);
				break;
			case "scorpion":
				updateAppearanceToNpc(affectedPlayer, SCORPION, pos);
				break;
			case "elvarg":
			case "greendragon":
				updateAppearanceToNpc(affectedPlayer, ELVARG, pos);
				break;
			case "reddragon":
				updateAppearanceToNpc(affectedPlayer, RED_DRAGON, pos);
				break;
			case "bluedragon":
				updateAppearanceToNpc(affectedPlayer, BLUE_DRAGON, pos);
				break;
			case "whitewolf":
				updateAppearanceToNpc(affectedPlayer, WHITE_WOLF, pos);
				break;
			case "greywolf":
			case "graywolf":
			case "wolf":
				updateAppearanceToNpc(affectedPlayer, GREY_WOLF, pos);
				break;
			case "firebird":
			case "firechicken":
				updateAppearanceToNpc(affectedPlayer, FIREBIRD, pos);
				break;

			case "guarddog":
			case "brownwolf":
				updateAppearanceToNpc(affectedPlayer, GUARD_DOG, pos);
				break;

			case "icespider":
			case "bluespider":
				updateAppearanceToNpc(affectedPlayer, ICE_SPIDER, pos);
				break;

			case "blackdemon":
				updateAppearanceToNpc(affectedPlayer, BLACK_DEMON, pos);
				break;
			case "blackdragon":
				updateAppearanceToNpc(affectedPlayer, BLACK_DRAGON, pos);
				break;
			case "poisonspider":
				updateAppearanceToNpc(affectedPlayer, POISON_SPIDER, pos);
				break;
			case "shadowwolf":
			case "blackwolf":
			case "hellhound":
			case "marwolf":
				updateAppearanceToNpc(affectedPlayer, HELLHOUND, pos);
				break;
			case "blackunicorn":
				updateAppearanceToNpc(affectedPlayer, BLACK_UNICORN, pos);
				break;
			case "darkreddemon":
			case "chronozon":
				updateAppearanceToNpc(affectedPlayer, CHRONOZON, pos);
				break;
			case "shadowspider":
			case "blackspider":
				updateAppearanceToNpc(affectedPlayer, SHADOW_SPIDER, pos);
				break;
			case "dungeonrat":
			case "lightrat":
				updateAppearanceToNpc(affectedPlayer, DUNGEON_RAT, pos);
				break;
			case "junglespider":
				updateAppearanceToNpc(affectedPlayer, JUNGLE_SPIDER, pos);
				break;
			case "souless":
			case "soulless":
				updateAppearanceToNpc(affectedPlayer, SOULESS, pos);
				break;
			case "desertwolf":
				updateAppearanceToNpc(affectedPlayer, DESERT_WOLF, pos);
				break;
			case "junglewolf":
			case "karamjawolf":
				updateAppearanceToNpc(affectedPlayer, KARAMJA_WOLF, pos);
				break;
			case "oomliebird":
				updateAppearanceToNpc(affectedPlayer, OOMLIE_BIRD, pos);
				break;
			case "bigbunny":
			case "bigbun":
			case "bigrabbit":
			case "bigchungus":
				updateAppearanceToNpc(affectedPlayer, BUNNY, pos);
				break;
			case "duck":
			case "mallard":
				updateAppearanceToNpc(affectedPlayer, DUCK, pos);
				break;
			case "bunny":
			case "bun":
			case "rabbit":
				updateAppearanceToNpc(affectedPlayer, BUNNY_MORPH, pos);
				break;
			case "egg":
				updateAppearanceToNpc(affectedPlayer, EGG_MORPH, pos);
				break;
			case "logg":
				updateAppearanceToScenery(affectedPlayer, 8); // pile of logs
				break;
			case "kenix":
				updateAppearanceToScenery(affectedPlayer, 407); // scary tree
				break;

			case "disable":
			case "none":
			case "reset":
				restoreHumanity(affectedPlayer);
				break;
			default:
				// didn't match any special non-humanoid monsters
				try {
					NpcId npcId = NpcId.getByName(npcName);
					int id = npcId.id();
					if (npcId == NpcId.NOBODY) {
						id = Integer.parseInt(npcName);
					}
					if (id > player.getClientLimitations().maxNpcId) {
						player.message("Your client might not support this NPC.");
					}
					NPCDef theNpc = new Npc(player.getWorld(), id, 0, 0, 0, 0, 0, 0).getDef();
					player.message("Transforming into " + theNpc.getName());
					restoreHumanity(affectedPlayer);

					affectedPlayer.getSettings().getAppearance().setNpcAppearance(theNpc);
					boolean swapWeaponShield = false;
					for (int p = 0; p < 12; p++) {
						if (theNpc.getSprite(p) + 1 >= 0 && theNpc.getSprite(p) <= player.getClientLimitations().maxAnimationId) {
							// Some NPCs authentically have their weapon & shield sprites in the wrong positions.
							// We can look up the animation IDs individually to see where they should go
							int wieldPosition = p;
							if (p == AppearanceId.SLOT_WEAPON || p == AppearanceId.SLOT_SHIELD) {
								AppearanceId appearanceId = AppearanceId.getById(theNpc.getSprite(p) + 1);
								if (appearanceId != NOTHING) {
									if (appearanceId.getSuggestedWieldPosition() != p) {
										swapWeaponShield = true;
										wieldPosition = appearanceId.getSuggestedWieldPosition();
									}
								}
							}

							// update worn items (if not overwriting weapon with shield that doesn't exist)
							if (!(p == AppearanceId.SLOT_WEAPON && swapWeaponShield && theNpc.getSprite(p) == -1)) {
								affectedPlayer.updateWornItems(wieldPosition, theNpc.getSprite(p) + 1);
							}
						}
					}
				} catch (Exception e) {
					player.message("Could not find an npc named " + npcName);
				}
		}
		affectedPlayer.getUpdateFlags().setAppearanceChanged(true);
	}

	private void updateAppearanceToScenery(Player targetedPlayer, int sceneryId) {
		// match permission level of ::robject
		if (!targetedPlayer.isDev()) return;

		// ::norender
		for (int i = 0; i < 12; i++) {
			targetedPlayer.updateWornItems(i, 0);
		}

		// set sceneryId for use when moving
		targetedPlayer.setSceneryMorph(sceneryId);

		// register scenery @ standing location (since targeted player has not moved yet)
		final GameObject existingObject = targetedPlayer.getViewArea().getGameObject(targetedPlayer.getLocation());
		if (existingObject == null || existingObject.getType() == 0) {
			final GameObject newObject = new GameObject(targetedPlayer.getWorld(), targetedPlayer.getLocation(), sceneryId, 0, 0);
			targetedPlayer.getWorld().registerGameObject(newObject);
		}
	}

	private void updateAppearanceToNpc(Player player, AppearanceId appearanceId, int wieldPosition) {
		if (wieldPosition == SLOT_ANY) {
			mes("Don't know where to wield it, sorry");
			return;
		}
		int id = appearanceId.id();
		if (player.isUsing38CompatibleClient() || player.isUsing39CompatibleClient()) {
			id = AppearanceRetroConverter.convert(appearanceId.id());
		}
		if (id > player.getClientLimitations().maxAnimationId || id == 0) {
			mes("Your client doesn't know about that NPC.");
			return;
		}
		player.resetSceneryMorph();
		if (wieldPosition == SLOT_NPC) {
			for (int pos = 0; pos < 12; pos++) {
				if (pos != SLOT_WEAPON) {
					player.updateWornItems(pos, NOTHING);
				}
			}
			player.updateWornItems(SLOT_BODY, appearanceId);
			return;
		}
		player.updateWornItems(wieldPosition, appearanceId);
		player.getUpdateFlags().setAppearanceChanged(true);
	}

	private void becomeGod(Player player) {
		// TODO: God could be more sophisticated
		for (int i = 0; i < 12; i++) {
			player.updateWornItems(i, random(124, 180));
		}
		player.getUpdateFlags().setAppearanceChanged(true);

		speakTongues(player, 1);
	}

	private void speakTongues(Player player, int state) {
		boolean lastTongues = player.speakTongues;
		switch (state) {
			case 0:
				player.speakTongues = false;
				break;
			case 1:
				player.speakTongues = true;
				break;
			default:
				player.speakTongues = !player.speakTongues;
		}

		if (player.speakTongues != lastTongues) {
			if (player.speakTongues) {
				mes(DataConversions.speakTongues("You are now speaking in the tongue of the gods."));
			} else {
				mes("You are now speaking in the mortal tongue.");
			}
		}
	}

	private void restoreHumanity(Player player) {
		speakTongues(player, 0);
		player.exitMorph();
		player.resetSceneryMorph();
	}

	private void restoreHumanity(Player player, String[] args) {
		if (args.length > 0) {
			if (player.isAdmin()) {
				Player affectedPlayer = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
				if (affectedPlayer == null) {
					mes("Couldn't find that player.");
					return;
				}
				restoreHumanity(affectedPlayer);
			} else {
				mes("Sorry, but you must be an admin to restore the humanity of others.");
			}
		} else {
			restoreHumanity(player);
		}

	}
}
