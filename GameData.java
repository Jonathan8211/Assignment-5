import java.io.Serializable;

/**
 * Game data transfer model used for serialized data exchange between server and client
 * Encapsulates core information such as player operation commands, game status, username, move position, etc.
 * @author Chen Junliang
 */
public class GameData implements Serializable {
    // Command types: NAME(submit name), MOVE(make a move), WIN(victory), DRAW(tie), EXIT(exit), RESTART(restart)
    public enum Command { NAME, MOVE, WIN, DRAW, EXIT, RESTART }

    private Command command;
    private String playerName;
    private int row;
    private int col;
    private int p1Wins;
    private int p2Wins;
    private int draws;

    /**
     * Constructor: Initialize data according to different command types
     * @param command Command type
     * @param playerName Player name
     * @param row Row number of the move (only valid for MOVE command)
     * @param col Column number of the move (only valid for MOVE command)
     * @param p1Wins Number of wins for player 1
     * @param p2Wins Number of wins for player 2
     * @param draws Number of ties
     */
    public GameData(Command command, String playerName, int row, int col, int p1Wins, int p2Wins, int draws) {
        this.command = command;
        this.playerName = playerName;
        this.row = row;
        this.col = col;
        this.p1Wins = p1Wins;
        this.p2Wins = p2Wins;
        this.draws = draws;
    }

    // Getters (JavaDoc required for non-private members to meet assignment requirements)
    /**
     * Get the command type
     * @return Command enumeration value
     */
    public Command getCommand() { return command; }

    /**
     * Get the player name
     * @return The name entered by the player
     */
    public String getPlayerName() { return playerName; }

    /**
     * Get the row number of the move
     * @return Row index (0-2)
     */
    public int getRow() { return row; }

    /**
     * Get the column number of the move
     * @return Column index (0-2)
     */
    public int getCol() { return col; }

    /**
     * Get the number of wins for player 1
     * @return Cumulative number of wins
     */
    public int getP1Wins() { return p1Wins; }

    /**
     * Get the number of wins for player 2
     * @return Cumulative number of wins
     */
    public int getP2Wins() { return p2Wins; }

    /**
     * Get the number of ties
     * @return Cumulative number of ties
     */
    public int getDraws() { return draws; }
}
