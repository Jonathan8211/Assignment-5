import java.io.Serializable;

/**
 * Message class for client-server communication.
 */
public class GameMessage implements Serializable {
    public String type;
    public String content;
    public int p1Wins, p2Wins, draws;

    public GameMessage(String type, String content, int p1Wins, int p2Wins, int draws) {
        this.type = type;
        this.content = content;
        this.p1Wins = p1Wins;
        this.p2Wins = p2Wins;
        this.draws = draws;
    }
}