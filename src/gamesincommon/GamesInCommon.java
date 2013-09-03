package gamesincommon;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.github.koraktor.steamcondenser.steam.community.SteamGame;
import com.github.koraktor.steamcondenser.steam.community.SteamId;

public class GamesInCommon {

	Connection connection = null;

	private Logger logger;

	public GamesInCommon() {
		// initialise logger
		logger = Logger.getLogger(GamesInCommon.class.getName());
		logger.setLevel(Level.ALL);
		// initialise database connector
		connection = InitialDBCheck();
		if (connection == null) {
			throw new RuntimeException("Connection could not be establised to local database.");
		}
	}

	public Logger getLogger() {
		return logger;
	}

	/**
	 * Creates local database, if necessary, and creates tables for all enum entries.
	 * 
	 * @return A Connection object to the database.
	 */
	private Connection InitialDBCheck() {
		// newDB is TRUE if the database is about to be created by DriverManager.getConnection();
		File dbFile = new File("gamedata.db");
		boolean newDB = (!(dbFile).exists());

		Connection result = null;
		try {
			Class.forName("org.sqlite.JDBC");
			// attempt to connect to the database
			result = DriverManager.getConnection("jdbc:sqlite:gamedata.db");
			// check all tables from the information schema
			Statement statement = result.createStatement();
			ResultSet resultSet = null;
			// and copy resultset to List object to enable random access
			List<String> tableList = new ArrayList<String>();
			// skip if new database, as it'll all be new anyway
			if (!newDB) {
				// query db
				resultSet = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table';");
				// copy to tableList
				while (resultSet.next()) {
					tableList.add(resultSet.getString("name"));
				}
			} else {
				logger.log(Level.INFO, "New database created.");
			}
			// check all filtertypes have a corresponding table, create if one if not present
			// skip check and create if the database is new
			for (FilterType filter : FilterType.values()) {
				boolean filterFound = false;
				if (!newDB) {
					for (String tableName : tableList) {
						if (tableName.equals(filter.getValue())) {
							filterFound = true;
						}
					}
				}
				// if the tableList is traversed and the filter was not found, create a table for it
				if (!filterFound) {
					statement.executeUpdate("CREATE TABLE [" + filter.getValue() + "] ( AppID VARCHAR( 16 )  PRIMARY KEY ON CONFLICT FAIL,"
							+ "Name VARCHAR( 64 )," + "HasProperty BOOLEAN NOT NULL ON CONFLICT FAIL );");
				}
			}
		} catch (ClassNotFoundException | SQLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return result;
	}

	/**
	 * Finds common games between an arbitrarily long list of users
	 * 
	 * @param users
	 *            A list of names to find common games for.
	 * @return A collection of games common to all users
	 */
	public Collection<SteamGame> findCommonGames(List<SteamId> users) {

		List<Collection<SteamGame>> userGames = new ArrayList<Collection<SteamGame>>();

		for (SteamId name : users) {
			try {
				userGames.add(getGames(name));
				logger.log(Level.INFO, "Added user " + name.getNickname() + " (" + name.getSteamId64() + ").");
			} catch (SteamCondenserException e) {
				logger.log(Level.SEVERE, e.getMessage(), e);
				return null;
			}
		}

		System.out.print("Finding common games... ");
		Collection<SteamGame> commonGames = mergeSets(userGames);
		logger.log(Level.INFO, "found.");

		return commonGames;
	}

	public SteamId checkSteamId(String nameToCheck) throws SteamCondenserException {
		try {
			return SteamId.create(nameToCheck);
		} catch (SteamCondenserException e1) {
			try {
				return SteamId.create(Long.parseLong(nameToCheck));
			} catch (SteamCondenserException | NumberFormatException e2) {
				throw e1;
			}
		}
	}

	/**
	 * Finds all games from the given steam user.
	 * 
	 * @param sId
	 *            The SteamId of the user to get games from.
	 * @return A set of all games for the give user.
	 * @throws SteamCondenserException
	 */
	public Collection<SteamGame> getGames(SteamId sId) throws SteamCondenserException {
		return sId.getGames().values();
	}

	/**
	 * Returns games that match one or more of the given filter types
	 * 
	 * @param gameList
	 *            Collection of games to be filtered.
	 * @param filterList
	 *            Collection of filters to apply.
	 * @return A collection of games matching one or more of filters.
	 */
	public Collection<SteamGame> filterGames(Collection<SteamGame> gameList, List<FilterType> filterList) {

		Collection<SteamGame> result = new HashSet<SteamGame>();

		for (SteamGame game : gameList) {
			// first run a query through the local db
			boolean checkWeb = true;
			try {
				Statement s = connection.createStatement();
				// get list of tables
				ResultSet tableSet = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table';");
				// query the table that matches the filter
				while (tableSet.next()) {
					for (FilterType filter : filterList) {
						// WARNING - This does NOT trigger a webpage update if one or more filters have no DB data, but at least one filter
						// is TRUE
						ResultSet rSet = null;
						if (filter.getValue().equals((tableSet.getString("name")))) {
							rSet = s.executeQuery("SELECT * FROM [" + tableSet.getString("name") + "] WHERE AppID = '" + game.getAppId()
									+ "'");
						}
						// if rSet.next() indicates a match
						while ((rSet != null) && (rSet.next())) {
							// if the game passes the filter and is not already in the result collection, add it
							if (rSet.getBoolean("HasProperty") && (!result.contains(game))) {
								result.add(game);
							}
							// if there's an entry in the database, no need to check anywhere else
							checkWeb = false;
							logger.log(Level.INFO, "[SQL] Checked game '" + game.getName() + "'");
						}
					}
				}
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			// if checkWeb never got turned to false, we need to fetch data from the steampowered.com website
			if (checkWeb) {
				// foundProperties records whether it has or does not have each of the filters
				HashMap<FilterType, Boolean> foundProperties = new HashMap<FilterType, Boolean>();
				try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL(
						"http://store.steampowered.com/api/appdetails/?appids=" + game.getAppId()).openStream()));) {
					// Read lines in until there are no more to be read, run filter on each line looking for specified package IDs.
					String line;
					while (((line = br.readLine()) != null) && (!result.contains(game))) {
						for (FilterType filter : filterList) {
							// default false until set to true
							foundProperties.put(filter, false);
							if (line.contains("\"" + filter.getValue() + "\"")) {
								result.add(game);
								// success - add to db
								connection.createStatement().executeUpdate(
										"INSERT INTO [" + filter.getValue() + "] (AppID, Name, HasProperty) VALUES ('" + game.getAppId()
												+ "','" + sanitiseInputString(game.getName()) + "', 1)");
								foundProperties.put(filter, true);
							}
						}
					}
					// insert filters returning false as false to the DB
					for (Map.Entry<FilterType, Boolean> entry : foundProperties.entrySet()) {
						if (entry.getValue().equals(new Boolean(false))) {
							connection.createStatement().executeUpdate(
									"INSERT INTO [" + entry.getKey().toString() + "] (AppID, Name, HasProperty) VALUES ('"
											+ game.getAppId() + "','" + sanitiseInputString(game.getName()) + "', 0)");
						}
					}
					logger.log(Level.INFO, "[WEB] Checked game '" + game.getName() + "'");

				} catch (IOException | SQLException e) {
					logger.log(Level.SEVERE, e.getMessage(), e);
				}
			}

		}
		return result;
	}

	/**
	 * Merges multiple user game sets together to keep all games that are the same.
	 * 
	 * @param userGames
	 *            A list of user game sets.
	 * @return A set containing all common games.
	 */
	public Collection<SteamGame> mergeSets(List<Collection<SteamGame>> userGames) {

		if (userGames.size() == 0) {
			return null;
		}

		Collection<SteamGame> result = userGames.get(0);

		for (int i = 1; i < userGames.size(); i++) {
			result.retainAll(userGames.get(i));
		}

		return result;

	}

	public String sanitiseInputString(String input) {
		return input.replace("'", "''");
	}

}
