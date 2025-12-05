import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Two-player Tic Tac Toe client that connects to local server (127.0.0.1:8888)
 * Reuses GUI layout from Assignment 4, adds network communication and two-player battle logic
 * @author Chen Junliang
 */
public class TicTacToeClient implements ActionListener {
    private JFrame mainFrame;
    private JTextField nameTextField;
    private JButton submitBtn;
    private JLabel messageLabel;
    private JLabel p1WinLabel;
    private JLabel p2WinLabel;
    private JLabel drawLabel;
    private JLabel timeLabel;
    private JButton[][] boardButtons;
    private JMenuBar menuBar;
    private JMenu controlMenu;
    private JMenu helpMenu;
    private JMenuItem exitItem;
    private JMenuItem instructionItem;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String playerName;
    private int playerId; // 1=Player 1(X), 2=Player 2(O)
    private boolean isMyTurn;
    private boolean isGameActive;
    private int p1Wins;
    private int p2Wins;
    private int draws;
    private Timer timeTimer;

    /**
     * Client initialization: create GUI, connect to server, start time display
     */
    public TicTacToeClient() {
        initGUI();
        connectToServer();
        startTimeDisplay();
    }

    /**
     * Initialize GUI components, following Assignment 4 layout style
     */
    private void initGUI() {
        mainFrame = new JFrame("Tic Tac Toe");
        mainFrame.setSize(600, 550);
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout());

        // Menu bar
        menuBar = new JMenuBar();
        controlMenu = new JMenu("Control");
        helpMenu = new JMenu("Help");
        exitItem = new JMenuItem("Exit");
        instructionItem = new JMenuItem("Instruction");

        exitItem.addActionListener(e -> exitGame());
        instructionItem.addActionListener(e -> showInstruction());
        controlMenu.add(exitItem);
        helpMenu.add(instructionItem);
        menuBar.add(controlMenu);
        menuBar.add(helpMenu);
        mainFrame.setJMenuBar(menuBar);

        // Message label
        messageLabel = new JLabel("Enter your player name…", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Arial", Font.BOLD, 16));
        mainFrame.add(messageLabel, BorderLayout.NORTH);

        // Center panel (board + scores)
        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Board panel
        JPanel boardPanel = new JPanel(new GridLayout(3, 3, 5, 5));
        boardButtons = new JButton[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                boardButtons[i][j] = new JButton("");
                boardButtons[i][j].setFont(new Font("Arial", Font.BOLD, 50));
                boardButtons[i][j].setBackground(Color.WHITE);
                boardButtons[i][j].setFocusPainted(false);
                boardButtons[i][j].addActionListener(this);
                boardPanel.add(boardButtons[i][j]);
            }
        }
        centerPanel.add(boardPanel, BorderLayout.CENTER);

        // Score panel
        JPanel scorePanel = new JPanel(new BorderLayout());
        scorePanel.setPreferredSize(new Dimension(180, 0));
        JLabel scoreTitle = new JLabel("Score", SwingConstants.CENTER);
        scoreTitle.setFont(new Font("Arial", Font.BOLD, 18));
        scoreTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        scorePanel.add(scoreTitle, BorderLayout.NORTH);

        JPanel scoreLabels = new JPanel(new GridLayout(3, 1, 0, 20));
        p1WinLabel = new JLabel("Player 1 Wins: 0", SwingConstants.CENTER);
        p2WinLabel = new JLabel("Player 2 Wins: 0", SwingConstants.CENTER);
        drawLabel = new JLabel("Draws: 0", SwingConstants.CENTER);
        scoreLabels.add(p1WinLabel);
        scoreLabels.add(p2WinLabel);
        scoreLabels.add(drawLabel);
        scorePanel.add(scoreLabels, BorderLayout.CENTER);
        centerPanel.add(scorePanel, BorderLayout.EAST);

        mainFrame.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel (name input + time)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setPreferredSize(new Dimension(0, 80));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JPanel namePanel = new JPanel();
        namePanel.add(new JLabel("Name: "));
        nameTextField = new JTextField(15);
        submitBtn = new JButton("Submit");
        submitBtn.addActionListener(e -> submitName());
        namePanel.add(nameTextField);
        namePanel.add(submitBtn);
        bottomPanel.add(namePanel, BorderLayout.NORTH);

        timeLabel = new JLabel("", SwingConstants.CENTER);
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        bottomPanel.add(timeLabel, BorderLayout.SOUTH);
        mainFrame.add(bottomPanel, BorderLayout.SOUTH);

        // Initial state
        isGameActive = false;
        isMyTurn = false;
        p1Wins = 0;
        p2Wins = 0;
        draws = 0;
        mainFrame.setVisible(true);

        // Window closing event (equivalent to Exit)
        mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                exitGame();
            }
        });
    }

    /**
     * Connect to local server (127.0.0.1:8888)
     */
    private void connectToServer() {
        try {
            socket = new Socket("127.0.0.1", 8888);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            messageLabel.setText("Connected to server. Waiting for opponent...");

            // Start thread to listen for server messages
            new Thread(this::listenToServer).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainFrame, "Failed to connect to server!", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    /**
     * Listen for messages sent by the server and handle different commands
     */
    private void listenToServer() {
        try {
            while (true) {
                GameData data = (GameData) in.readObject();
                switch (data.getCommand()) {
                    // Replace NAME branch in Client's listenToServer
                    case NAME:
                        // The row parameter sent by the server = opponent's player ID
                        int opponentId = data.getRow();
                        // Own ID is the opposite of opponent's ID
                        playerId = (opponentId == 1) ? 2 : 1;
                        isMyTurn = (playerId == 1); // Only player 1 moves first
                        
                        // Save opponent's name and update message label (modified)
                        String opponentName = data.getPlayerName();
                        // Handle case where opponent's name may be empty
                        if (opponentName == null || opponentName.isEmpty()) {
                            opponentName = "Opponent";
                        }
                        
                        messageLabel.setText(
                            "WELCOME " + playerName + " (Player " + playerId + ": " + (playerId == 1 ? "X" : "O") + ")\n" +
                            "Opponent: " + opponentName
                        );
                        mainFrame.setTitle("Tic Tac Toe - Player: " + playerName);
                        isGameActive = true; // Game activates after both players submit names
                        break;

                    case MOVE:
                        // Handle opponent's move, update local board
                        int row = data.getRow();
                        int col = data.getCol();
                        String opponentMark = playerId == 1 ? "O" : "X";
                        Color opponentColor = playerId == 1 ? Color.BLUE : Color.RED;
                        boardButtons[row][col].setText(opponentMark);
                        boardButtons[row][col].setForeground(opponentColor);
                        boardButtons[row][col].setBackground(Color.LIGHT_GRAY);

                        // Switch to own turn
                        isMyTurn = true;
                        messageLabel.setText("Your opponent has moved, now is your turn.");
                        break;

                    case WIN:
                        // Handle win/loss result
                        p1Wins = data.getP1Wins();
                        p2Wins = data.getP2Wins();
                        updateScoreLabels();
                        String winMsg = data.getPlayerName().equals(playerName) ? "You win!" : "You lose!";
                        showGameOverDialog(winMsg);
                        break;

                    case DRAW:
                        // Handle tie result
                        draws = data.getDraws();
                        updateScoreLabels();
                        showGameOverDialog("It's a draw!");
                        break;

                    case EXIT:
                        // Opponent exited
                        JOptionPane.showMessageDialog(mainFrame, "Game Ends. One of the players left.", "Game Over", JOptionPane.INFORMATION_MESSAGE);
                        exitGame();
                        break;

                    case RESTART:
                        // Restart game
                        resetBoard();
                        p1Wins = data.getP1Wins();
                        p2Wins = data.getP2Wins();
                        draws = data.getDraws();
                        updateScoreLabels();
                        isGameActive = true;
                        isMyTurn = (playerId == 1); // Player 1 moves first
                        messageLabel.setText(isMyTurn ? "Your turn (X)" : "Waiting for opponent (O)");
                        break;
                }
            }
        } catch (SocketException e) {
            JOptionPane.showMessageDialog(mainFrame, "Server disconnected!", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            closeConnections();
            System.exit(0);
        }
    }

    /**
     * Submit player name to server and disable input field
     */
    private void submitName() {
        playerName = nameTextField.getText().trim();
        if (playerName.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "Please enter your name!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Send name to server
            out.writeObject(new GameData(GameData.Command.NAME, playerName, 0, 0, p1Wins, p2Wins, draws));
            nameTextField.setEnabled(false);
            submitBtn.setEnabled(false);
            messageLabel.setText("Waiting for opponent to submit name..."); // More explicit prompt
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handle board button clicks (move logic)
     * @param e Action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isGameActive || !isMyTurn) return;

        JButton clickedBtn = (JButton) e.getSource();
        if (!clickedBtn.getText().equals("")) return; // Skip already occupied positions

        // Find move coordinates
        int row = -1, col = -1;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (boardButtons[i][j] == clickedBtn) {
                    row = i;
                    col = j;
                    break;
                }
            }
        }

        // Update local move
        String myMark = playerId == 1 ? "X" : "O";
        Color myColor = playerId == 1 ? Color.RED : Color.BLUE;
        clickedBtn.setText(myMark);
        clickedBtn.setForeground(myColor);
        clickedBtn.setBackground(Color.LIGHT_GRAY);

        // Send move information to server
        try {
            out.writeObject(new GameData(GameData.Command.MOVE, playerName, row, col, p1Wins, p2Wins, draws));
            isMyTurn = false;
            messageLabel.setText("Valid move, waiting for your opponent.");
            isGameActive = true;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Show game over dialog, ask to restart
     * @param resultMsg Game result message
     */
    private void showGameOverDialog(String resultMsg) {
        int option = JOptionPane.showConfirmDialog(
                mainFrame,
                resultMsg + "\nRestart the game?",
                "Game Over",
                JOptionPane.YES_NO_OPTION
        );

        try {
            if (option == JOptionPane.YES_OPTION) {
                out.writeObject(new GameData(GameData.Command.RESTART, playerName, 0, 0, p1Wins, p2Wins, draws));
            } else {
                exitGame();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reset board state (clear moves, restore colors)
     */
    private void resetBoard() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                boardButtons[i][j].setText("");
                boardButtons[i][j].setBackground(Color.WHITE);
                boardButtons[i][j].setForeground(Color.BLACK);
            }
        }
    }

    /**
     * Update score label display
     */
    private void updateScoreLabels() {
        p1WinLabel.setText("Player 1 Wins: " + p1Wins);
        p2WinLabel.setText("Player 2 Wins: " + p2Wins);
        drawLabel.setText("Draws: " + draws);
    }

    /**
     * Start real-time time display (updates every second)
     */
    private void startTimeDisplay() {
        timeTimer = new Timer(1000, e -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            timeLabel.setText("Current Time: " + time);
        });
        timeTimer.start();
        updateScoreLabels(); // Initialize score display
    }

    /**
     * Show game instructions
     */
    private void showInstruction() {
        String instruction = "Tic Tac Toe Instructions:\n" +
                "- Enter your name to join the game.\n" +
                "- Player 1 plays as 'X' (red), Player 2 as 'O' (blue).\n" +
                "- Player 1 starts first.\n" +
                "- Valid move: Empty cell + your turn + within 3×3 board.\n" +
                "- Win by aligning 3 marks in a row, column, or diagonal.\n" +
                "- If opponent leaves, game ends immediately.";
        JOptionPane.showMessageDialog(mainFrame, instruction, "Instruction", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Exit game: send exit command, close connections
     */
    private void exitGame() {
        try {
            if (out != null) {
                out.writeObject(new GameData(GameData.Command.EXIT, playerName, 0, 0, p1Wins, p2Wins, draws));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnections();
            System.exit(0);
        }
    }

    /**
     * Close network connections and timer
     */
    private void closeConnections() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            if (timeTimer != null) timeTimer.stop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method: start the client
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(TicTacToeClient::new);
    }
}
