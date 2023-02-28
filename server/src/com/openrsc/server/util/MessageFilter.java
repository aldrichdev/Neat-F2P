package com.openrsc.server.util;

import com.openrsc.server.model.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class MessageFilter {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();
	private static ArrayList<String> badwords = new ArrayList<String>();
	private static ArrayList<String> goodwords = new ArrayList<String>();

	public static Pair<Integer, Integer> loadGoodAndBadWordsFromDisk() {
		List<String> lines = Collections.emptyList();

		// reinitialize in case this function has been called post-server boot
		badwords = new ArrayList<String>();
		goodwords = new ArrayList<String>();
		int goodwordCount = 0;
		int badwordCount = 0;

		// BADWORDS
		try {
			lines = Files.readAllLines(Paths.get("badwords.txt"));

			for (String line : lines) {
				line = line.toLowerCase();
				if (line.length() >= 3) {
					badwords.add(line);
					++badwordCount;
				} else {
					LOGGER.info("Skipped word \"" + line + "\" for being too short.");
				}
			}

			LOGGER.info("Successfully loaded " + badwordCount + " badwords.");
		} catch (IOException e) {
			LOGGER.warn("Could not find badwords.txt near server config file.");
		}

		// GOODWORDS
		try {
			lines = Files.readAllLines(Paths.get("goodwords.txt"));

			for (String line : lines) {
				line = line.toLowerCase();
				if (line.length() >= 3) {
					goodwords.add(line);
					++goodwordCount;
				} else {
					LOGGER.info("Skipped word \"" + line + "\" for being too short.");
				}
			}

			LOGGER.info("Successfully loaded " + goodwordCount + " goodwords.");
		} catch (IOException e) {
			LOGGER.warn("Could not find goodwords.txt near server config file.");
		}

		return Pair.of(goodwordCount, badwordCount);
	}

	public static boolean addBadWord(String badword) {
		return badwords.add(badword.toLowerCase());
	}

	public static boolean removeBadWord(String oldbadword) {
		return badwords.remove(oldbadword.toLowerCase());
	}

	public static boolean addGoodWord(String goodword) {
		return goodwords.add(goodword.toLowerCase());
	}

	public static boolean removeGoodWord(String oldgoodword) {
		return goodwords.remove(oldgoodword.toLowerCase());
	}

	public static String filter(Player sender, String message, String context) {
		if (!sender.getConfig().SERVER_SIDED_WORD_FILTERING) {
			return message;
		}

		if (message.length() == 0) {
			return "Cabbage";
		}

		ArrayList<String> stringProblems = new ArrayList<>();
		String originalMessage = message;
		Map<Integer, String> formatCodes = new TreeMap<>();

		try {
			// check for, save, and remove indices of format codes
			for (int i = 0; i < message.length() - 4; i++) {
				if (('@' == message.charAt(i) && '@' == message.charAt(i + 4)) ||
					('~' == message.charAt(i) && '~' == message.charAt(i + 4))) {
					formatCodes.put(i, message.substring(i, i + 5));
					i += 4;
				}
			}
			message = message.replaceAll("@...@|~...~", "");
			String messageLowercase = message.toLowerCase();

			// check for and save goodword matches
			HashMap<Integer, String> goodwordsReplacements = new HashMap<>();
			for (String goodword : goodwords) {
				while (messageLowercase.contains(goodword)) {
					int goodwordIndex = messageLowercase.indexOf(goodword);
					String originalGoodwordUserCapitalization = message.substring(goodwordIndex, goodwordIndex + goodword.length());

					goodwordsReplacements.put(goodwordIndex, originalGoodwordUserCapitalization);
					message = message.replace(originalGoodwordUserCapitalization, padAsterisk(originalGoodwordUserCapitalization.length()));
					messageLowercase = message.toLowerCase();
				}
			}

			// censor badwords, with or without filler characters
			for (String badword : badwords) {
				while (messageLowercase.contains(badword)) {
					stringProblems.add("badword: " + badword);
					int badIndex = messageLowercase.indexOf(badword);
					int badwordLength = badword.length();
					message = replaceAtIndexWithAsterisks(message, badIndex, badwordLength);
					messageLowercase = message.toLowerCase();
				}

				if (alphaNumericMessageContains(messageLowercase, badword)) {
					stringProblems.add("b a d w o r d: " + badword);
					message = filterSpaces(message, badword, false);
				}
			}

			// NOW check that there aren't badwords in the filtered message obfuscated with common 1337 speak, with or without spaces
			String de1337edMessage = de1337(message);
			if (!de1337edMessage.equalsIgnoreCase(message)) {
				for (String goodword : goodwords) {
					// avoid false flags. Not necessary to keep track of indices since this de1337edMessage is not returned.
					de1337edMessage = de1337edMessage.replaceAll(goodword, padAsterisk(goodword.length()));
				}
				for (String badword : badwords) {
					String de1337Badword = de1337(badword);
					int badIndex = de1337edMessage.indexOf(de1337Badword);
					while (badIndex != -1) {
						stringProblems.add("1337 badword: " + badword);
						int badwordLength = badword.length();
						message = replaceAtIndexWithAsterisks(message, badIndex, badwordLength);
						de1337edMessage = de1337(message);
						badIndex = de1337edMessage.indexOf(de1337Badword);
					}

					if (alphaNumericMessageContains(de1337(message), de1337(badword))) {
						stringProblems.add("1 3 3 7   b a d w o r d: " + badword);
						message = filterSpaces(message, badword, true);
					}
				}
			}

			// replace goodword placeholders with original goodword saved
			for (int index : goodwordsReplacements.keySet()) {
				message = message.substring(0, index) + goodwordsReplacements.get(index) + message.substring(index + goodwordsReplacements.get(index).length());
			}

			// finally, restore format codes
			for (Map.Entry<Integer, String> pair : formatCodes.entrySet()) {
				String code = pair.getValue();
				// ensure that code does not contain bad word (to show to 3rd party client users)
				for (String badword : badwords) {
					if (badword.length() > 5) continue;
					if (code.contains(badword) || de1337(code).contains(de1337(badword))) {
						stringProblems.add("format code badword: " + badword);
						if (code.startsWith("@")) {
							code = "@***@";
						} else {
							code = "~***~";
						}
					}
				}

				message = insertAtPosition(message, pair.getKey(), code);
			}

			if (stringProblems.size() > 0) {
				sender.getWorld().getServer().getDiscordService().reportNaughtyWordToDiscord(sender, originalMessage, message, stringProblems, context);
			}
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			stringProblems.add("error filtering string: " + sw.toString());
			sender.getWorld().getServer().getDiscordService().reportNaughtyWordToDiscord(sender, originalMessage, "Cabbage", stringProblems, context);
			e.printStackTrace();
			return "Cabbage";
		}

		return message;
	}

	private static String insertAtPosition(String message, int index, String insertMe) {
		if (index >= message.length()) {
			return message + insertMe;
		}
		return message.substring(0, index) + insertMe + message.substring(index);
	}

	private static boolean alphaNumericMessageContains(String messageLowercase, String search) {
		messageLowercase = messageLowercase.replaceAll("[^a-z0-9]", "");
		search = search.replaceAll("[^a-z0-9]", "");
		return messageLowercase.contains(search);
	}

	private static String replaceAtIndexWithAsterisks(String message, int index, int numberOfAsterisks) {
		return message.substring(0, index) + padAsterisk(numberOfAsterisks) + message.substring(index + numberOfAsterisks);
	}

	private static String filterSpaces(String message, String badword, boolean leetspeak) {
		String messageLowercase = message.toLowerCase();
		if (leetspeak) {
			messageLowercase = de1337(messageLowercase);
			badword = de1337(badword);
		}

		final int MESSAGE_LENGTH = message.length();
		final int BADWORD_LENGTH = badword.length();

		int badwordCharIndex = 0;
		String badwordChar = badword.substring(badwordCharIndex, badwordCharIndex + 1);
		int offset = 0;
		ArrayList<Integer> candidateIndexes = new ArrayList<>();
		while (messageLowercase.indexOf(badwordChar, offset) != -1 && offset < MESSAGE_LENGTH - 1) {
			int cand = messageLowercase.indexOf(badwordChar, offset);
			candidateIndexes.add(cand);
			offset = cand + 1;
		}

		for (int candidateIndex : candidateIndexes) {
			boolean candidateValid = true;
			int candidateLength = 0;
			char[] checkMe = messageLowercase.substring(candidateIndex).toCharArray();
			for (char character : checkMe) {
				try {
					badwordChar = badword.substring(badwordCharIndex, badwordCharIndex + 1);
				} catch (StringIndexOutOfBoundsException ex) {
					// sue me
					break;
				}
				String characterStr = "" + character;
				if (characterStr.matches("[^a-z0-9]")) {
					++candidateLength;
					continue;
				}
				if (!characterStr.equalsIgnoreCase(badwordChar)) {
					candidateValid = false;
					break;
				}
				++candidateLength;
				++badwordCharIndex;
				if (badwordCharIndex > BADWORD_LENGTH) {
					break;
				}
			}
			if (candidateValid) {
				message = replaceAtIndexWithAsterisks(message, candidateIndex, candidateLength);
			}
			badwordCharIndex = 0;
		}

		return message;
	}

	private static String padAsterisk(int length) {
		StringBuilder sbpa = new StringBuilder();
		while (sbpa.length() < length) {
			sbpa.append('*');
		}
		return sbpa.toString();
	}

	// normalizes string to replace common 1337 replacement characters
	private static String de1337(String message) {
		message = message.toLowerCase()
			.replaceAll("13", " b")
			.replaceAll("\\(\\)", "o ")
			.replaceAll("\\)\\(", "x ")
			.replaceAll("ph", " f")
			.replaceAll("vv", "w ")
			.replaceAll("3", "e")
			.replaceAll("7", "t")
			.replaceAll("4", "a")
			.replaceAll("@", "a")
			.replaceAll("0", "o")
			.replaceAll("y", "i")
			.replaceAll("l", "i")
			.replaceAll("j", "i")
			.replaceAll("!", "i")
			.replaceAll(":", "i")
			.replaceAll(";", "i")
			.replaceAll("5", "s")
			.replaceAll("z", "s")
			.replaceAll("\\$", "s")
			.replaceAll("1", "i")
			.replaceAll("\\(", "c")
			.replaceAll("v", "u")
			.replaceAll("9", "g")
			.replaceAll("6", "g");
		return message;
	}

	public static boolean badwordsContains(String newBadWord) {
		return badwords.contains(newBadWord);
	}
	public static boolean goodwordsContains(String newGoodWord) {
		return goodwords.contains(newGoodWord);
	}

	public static void syncBadwordsToDisk() {
		Path out = Paths.get("badwords.txt");
		try {
			Files.write(out, badwords, Charset.defaultCharset());
		} catch (IOException ex) {
			LOGGER.error("Unable to save badwords.txt!");
		}
	}

	public static void syncGoodwordsToDisk() {
		Path out = Paths.get("goodwords.txt");
		try {
			Files.write(out, goodwords, Charset.defaultCharset());
		} catch (IOException ex) {
			LOGGER.error("Unable to save goodwords.txt!");
		}
	}
}
