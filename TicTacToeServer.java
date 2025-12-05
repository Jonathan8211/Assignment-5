import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Two-player Tic Tac Toe server that listens on local port 8888 and supports two client connections
 * Responsible for forwarding player operations, synchronizing game status, determining wins and losses, and handling disconnection logic
 * @author Chen Junliang
 */
public class TicTacToeServer {
    private ServerSocket serverSocket;
    private Socket client1Socket;
    private Socket client2Socket;
    private ObjectOutputStream out1;
    private ObjectInputStream in1;
    private ObjectOutputStream out2;
    private ObjectInputStream in2;
    private boolean isServerRunning; // Whether the server is running
    private boolean isCurrentGameRunning; // Whether the current game is in progress
    private boolean isPlayer1Turn;
    private String p1Name;
    private String p2Name;
    private int p1Wins;
    private int p2Wins;
    private int draws;
    private String[][] board; // Server maintains global board status

    /**
     * Server initialization: start listening, initialize game status
     */
    public TicTacToeServer() {
        try {
            serverSocket = new ServerSocket(8888);
            System.out.println("Server started, listening on port 8888...");
            board = new String[3][3];
            resetBoard();
            isServerRunning = true; // Server remains running after startup
            isCurrentGameRunning = false; // Initial game not started
            isPlayer1Turn = true;
            p1Wins = 0;
            p2Wins = 0;
            draws = 0;
            acceptClients(); // Wait for two client connections
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Wait for and accept two client connections, initialize input and output streams
     * @throws IOException Network connection exception
     */
    private void acceptClients() throws IOException {
        // Accept first client (player 1)
        client1Socket = serverSocket.accept();
        out1 = new ObjectOutputStream(client1Socket.getOutputStream());
        in1 = new ObjectInputStream(client1Socket.getInputStream());
        System.out.println("Player 1 connected: " + client1Socket.getInetAddress());

        // Accept second client (player 2)
        client2Socket = serverSocket.accept();
        out2 = new ObjectOutputStream(client2Socket.getOutputStream());
        in2 = new ObjectInputStream(client2Socket.getInputStream());
        System.out.println("Player 2 connected: " + client2Socket.getInetAddress());

        startListeningClients(); // Start threads to listen for client messages
    }

    /**
     * Start two threads to listen for messages from the two clients respectively
     */
    private void startListeningClients() {
        // Listen for player 1 messages
        new Thread(() -> {
            try {
                while (isServerRunning) {
                    GameData data = (GameData) in1.readObject();
                    handleClientData(data, 1);
                }
            } catch (SocketException e) {
                System.out.println("Player 1 disconnected");
                try {
                    notifyOpponentExit(2);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }).start();

        // Listen for player 2 messages
        new Thread(() -> {
            try {
                while (isServerRunning) {
                    GameData data = (GameData) in2.readObject();
                    handleClientData(data, 2);
                }
            } catch (SocketException e) {
                System.out.println("Player 2 disconnected");
                try {
                    notifyOpponentExit(1);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Process data sent by clients and execute corresponding logic according to command type
     * @param data Game data transmitted by client
     * @param playerId Player ID (1 or 2)
     * @throws IOException Data sending exception
     */
    private void handleClientData(GameData data, int playerId) throws IOException {
        switch (data.getCommand()) {
            case NAME:
                // Save player name and synchronize "opponent's ID + name" to client
                if (playerId == 1) {
                    p1Name = data.getPlayerName();
                    // Send confirmation to player 1 (added)
                    out1.writeObject(new GameData(GameData.Command.NAME, p2Name, 2, 0, p1Wins, p2Wins, draws));
                } else {
                    p2Name = data.getPlayerName();
                    // Send to player 1: player 2's ID is 2 + name
                    out1.writeObject(new GameData(GameData.Command.NAME, p2Name, 2, 0, p1Wins, p2Wins, draws));
                }
                // Start current game after both players submit names (modified)
                if (p1Name != null && p2Name != null) {
                    isCurrentGameRunning = true;
                    // Send to player 2: player 1's ID is 1 + name (added)
                    out2.writeObject(new GameData(GameData.Command.NAME, p1Name, 1, 0, p1Wins, p2Wins, draws));
                }
                break;

            case MOVE:
                // Verify move validity (current game must be running)
                int row = data.getRow();
                int col = data.getCol();
                if (isCurrentGameRunning && isValidMove(row, col, playerId)) {
                    String mark = playerId == 1 ? "X" : "O";
                    board[row][col] = mark;

                    // Forward move information to opponent
                    GameData moveData = new GameData(GameData.Command.MOVE, "", row, col, p1Wins, p2Wins, draws);
                    if (playerId == 1) {
                        out2.writeObject(moveData);
                    } else {
                        out1.writeObject(moveData);
                    }

                    // Determine win or loss
                    if (checkWin(mark)) {
                        handleWin(playerId);
                        isCurrentGameRunning = false; // Game ends
                    } else if (isBoardFull()) {
                        handleDraw();
                        isCurrentGameRunning = false; // Game ends
                    } else {
                        switchTurn(); // Switch turns
                    }
                }
                break;

            case RESTART:
                // Restart game, reset board and turn
                resetBoard();
                isPlayer1Turn = true;
                isCurrentGameRunning = true; // Start new game after restart
                GameData restartData = new GameData(GameData.Command.RESTART, "", 0, 0, p1Wins, p2Wins, draws);
                out1.writeObject(restartData);
                out2.writeObject(restartData);
                break;

            case EXIT:
                // Handle player exit
                notifyOpponentExit(playerId == 1 ? 2 : 1);
                closeConnections();
                break;
        }
    }

    /**
     * Verify if a move is valid (empty board position + current player's turn)
     * @param row Move row number
     * @param col Move column number
     * @param playerId Player ID
     * @return true if valid, false otherwise
     */
    private boolean isValidMove(int row, int col, int playerId) {
        if (row < 0 || row >= 3 || col < 0 || col >= 3) return false;
        if (board[row][col] != null) return false;
        return (playerId == 1 && isPlayer1Turn) || (playerId == 2 && !isPlayer1Turn);
    }

    /**
     * Check if the specified mark has won (three in a row, column, or diagonal)
     * @param mark Player's mark (X or O)
     * @return true if won, false otherwise
     */
    private boolean checkWin(String mark) {
        // Check rows
        for (int i = 0; i < 3; i++) {
            if (mark.equals(board[i][0]) && mark.equals(board[i][1]) && mark.equals(board[i][2])) {
                return true;
            }
        }
        // Check columns
        for (int i = 0; i < 3; i++) {
            if (mark.equals(board[0][i]) && mark.equals(board[1][i]) && mark.equals(board[2][i])) {
                return true;
            }
        }
        // Check diagonals
        if (mark.equals(board[0][0]) && mark.equals(board[1][1]) && mark.equals(board[2][2])) {
            return true;
        }
        return mark.equals(board[0][2]) && mark.equals(board[1][1]) && mark.equals(board[2][0]);
    }

    /**
     * Check if the board is full (tie determination)
     * @return true if board is full, false otherwise
     */
    private boolean isBoardFull() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Handle player win logic, update scores and notify both players
     * @param winnerId Winning player ID (1 or 2)
     * @throws IOException Data sending exception
     */
    private void handleWin(int winnerId) throws IOException {
        if (winnerId == 1) p1Wins++;
        else p2Wins++;

        GameData winData = new GameData(
                GameData.Command.WIN,
                winnerId == 1 ? p1Name : p2Name,
                0, 0,
                p1Wins, p2Wins, draws
        );
        out1.writeObject(winData);
        out2.writeObject(winData);
    }

    /**
     * Handle tie logic, update scores and notify both players
     * @throws IOException Data sending exception
     */
    private void handleDraw() throws IOException {
        draws++;
        GameData drawData = new GameData(
                GameData.Command.DRAW,
                "", 0, 0,
                p1Wins, p2Wins, draws
        );
        out1.writeObject(drawData);
        out2.writeObject(drawData);
    }

    /**
     * Switch player turns
     */
    private void switchTurn() {
        isPlayer1Turn = !isPlayer1Turn;
    }

    /**
     * Notify opponent that a player has exited (declares IOException to be handled by caller)
     * @param opponentId Opponent player ID (1 or 2)
     * @throws IOException Data sending exception
     */
    private void notifyOpponentExit(int opponentId) throws IOException {
        GameData exitData = new GameData(GameData.Command.EXIT, "", 0, 0, p1Wins, p2Wins, draws);
        if (opponentId == 1 && out1 != null) {
            out1.writeObject(exitData);
        } else if (opponentId == 2 && out2 != null) {
            out2.writeObject(exitData);
        }
        closeConnections();
    }

    /**
     * Reset the board (clear all moves)
     */
    private void resetBoard() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                board[i][j] = null;
            }
        }
    }

    /**
     * Close all network connections and streams
     */
    private void closeConnections() {
        isServerRunning = false;
        try {
            if (in1 != null) in1.close();
            if (out1 != null) out1.close();
            if (client1Socket != null) client1Socket.close();
            if (in2 != null) in2.close();
            if (out2 != null) out2.close();
            if (client2Socket != null) client2Socket.close();
            if (serverSocket != null) serverSocket.close();
            System.out.println("Server connections closed");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method: start the server
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        new TicTacToeServer();
    }
}
