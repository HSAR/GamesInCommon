package gamesincommon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.github.koraktor.steamcondenser.steam.community.SteamGame;

public class Console {
  
  public static void main(String[] args) {

    GamesInCommon gamesInCommon = new GamesInCommon();
    Console console = new Console();
    List<String> users = console.getUsers();
    console.displayCommonGames(gamesInCommon.findCommonGames(users));

  }
  
  /**
   * Creates a list of users from user input.
   * 
   * @return The list of user names.
   */
  private List<String> getUsers() {

    List<String> users = new ArrayList<String>();

    System.out.println("Enter users one by one, typing 'FIN' when complete:");

    try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in));) {

      String input;
      input = br.readLine();

      while (!input.equals("FIN")) {

        users.add(input);
        input = br.readLine();

      }

    } catch (IOException e) {
      e.printStackTrace();
    }

    return users;
  }
  
  /**
   * Displays all games from a collection on console output
   * 
   * @param games
   *            The collection to print
   */
  private void displayCommonGames(Collection<SteamGame> games) {
    // Lists games in common.
    for (SteamGame i : games) {
      System.out.println(i.getName());
    }
    // Final count
    System.out.println("Total games in common: " + games.size());
  }

}
