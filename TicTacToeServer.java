import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * COMP2396 Assignment 5 - Tic-Tac-Toe Server
 * Handles two clients, synchronizes game state, manages turns, win/draw detection,
 * and handles client disconnection gracefully.
 *
 * @author Your Name
 */
public class TicTacToeServer {
    private static final int PORT = 12345;
    private Player[] players = new Player[2];
    private int currentPlayer = 0;
    private String[] board = new String[9];
    private int p1Wins = 0, p2Wins = 0, draws = 0;

    /**
     * Starts the server and waits for two clients to connect.
     */
    public static void main(String[] args) {
        new TicTacToeServer().start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Tic-Tac-Toe Server started on port " + PORT);
            ExecutorService pool = Executors.newFixedThreadPool(2);

            while (true) {
                if (players[0] == null || !players[0].isConnected) {
                    System.out.println("Waiting for Player 1...");
                    Socket socket = serverSocket.accept();
                    players[0] = new Player(socket, 0);
                    pool.execute(players[0]);
                }
                if (players[1] == null || !players[1].isConnected) {
                    System.out.println("Waiting for Player 2...");
                    Socket socket = serverSocket.accept();
                    players[1] = new Player(socket, 1);
                    pool.execute(players[1]);
                    startNewGame();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void startNewGame() {
        board = new String[9];
        currentPlayer = 0;
        broadcast(new GameMessage("START", null, p1Wins, p2Wins, draws));
        broadcast(new GameMessage("MESSAGE", "WELCOME " + players[currentPlayer].name, p1Wins, p2Wins, draws));
        players[currentPlayer].send(new GameMessage("YOUR_TURN", null, p1Wins, p2Wins, draws));
    }

    private synchronized void handleMove(int playerIndex, int position) {
        if (playerIndex != currentPlayer || board[position] != null) {
            players[playerIndex].send(new GameMessage("INVALID_MOVE", null, p1Wins, p2Wins, draws));
            return;
        }

        String mark = playerIndex == 0 ? "X" : "O";
        board[position] = mark;

        broadcast(new GameMessage("MOVE", position + "," + mark, p1Wins, p2Wins, draws));
        broadcast(new GameMessage("MESSAGE", "Valid move, wait for your opponent.", p1Wins, p2Wins, draws));

        if (checkWin(mark)) {
            String winner = playerIndex == 0 ? players[0].name : players[1].name;
            if (playerIndex == 0) p1Wins++; else p2Wins++;
            broadcast(new GameMessage("WIN", winner + " wins!", p1Wins, p2Wins, draws));
            return;
        }

        if (isDraw()) {
            draws++;
            broadcast(new GameMessage("DRAW", "It's a draw!", p1Wins, p2Wins, draws));
            return;
        }

        currentPlayer = 1 - currentPlayer;
        players[currentPlayer].send(new GameMessage("YOUR_TURN", null, p1Wins, p2Wins, draws));
        broadcast(new GameMessage("MESSAGE", "Your opponent has moved, now is your turn.", p1Wins, p2Wins, draws));
    }

    private boolean checkWin(String mark) {
        int[][] wins = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] w : wins) {
            if (board[w[0]] != null && board[w[0]].equals(mark) &&
                board[w[1]].equals(mark) && board[w[2]].equals(mark))
                return true;
        }
        return false;
    }

    private boolean isDraw() {
        for (String s : board) if (s == null) return false;
        return true;
    }

    private void broadcast(GameMessage msg) {
        for (Player p : players) {
            if (p != null && p.isConnected) {
                p.send(msg);
            }
        }
    }

    private class Player implements Runnable {
        Socket socket;
        ObjectOutputStream out;
        ObjectInputStream in;
        int index;
        String name = "Player " + (index + 1);
        boolean isConnected = true;

        public Player(Socket socket, int index) {
            this.socket = socket;
            this.index = index;
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void send(GameMessage msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                disconnect();
            }
        }

        private void disconnect() {
            isConnected = false;
            try { socket.close(); } catch (Exception ignored) {}
            broadcast(new GameMessage("OPPONENT_LEFT", "Game Ends. One of the players left.", p1Wins, p2Wins, draws));
            players[index] = null;
        }

        @Override
        public void run() {
            try {
                GameMessage msg;
                while ((msg = (GameMessage) in.readObject()) != null) {
                    switch (msg.type) {
                        case "NAME":
                            name = msg.content;
                            if (players[0] != null && players[1] != null &&
                                players[0].name != null && players[1].name != null) {
                                broadcast(new GameMessage("BOTH_READY", null, p1Wins, p2Wins, draws));
                            }
                            break;
                        case "MOVE":
                            int pos = Integer.parseInt(msg.content);
                            handleMove(index, pos);
                            break;
                        case "RESTART":
                            if (players[0] != null && players[1] != null) {
                                startNewGame();
                            }
                            break;
                        case "EXIT":
                            disconnect();
                            return;
                    }
                }
            } catch (Exception e) {
                disconnect();
            }
        }
    }
}
