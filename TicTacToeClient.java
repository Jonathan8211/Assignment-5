import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * COMP2396 Assignment 5 - Two-Player Networked Tic-Tac-Toe Client
 * Connects to server, plays against another player over network.
 * Fully implements all GUI and gameplay requirements of Assignment 5.
 *
 * @author Your Name
 */
public class TicTacToeClient implements ActionListener {
    private JFrame frame;
    private JButton[][] buttons = new JButton[3][3];
    private JLabel messageLabel, p1Label, p2Label, drawLabel, timeLabel;
    private JTextField nameField;
    private JButton submitButton;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String myMark;
    private boolean myTurn = false;
    private boolean nameSubmitted = false;
    private Timer clockTimer;

    /**
     * Constructor: Sets up GUI and connects to server.
     */
    public TicTacToeClient() {
        setupGUI();
        connectToServer();
        startClock();
    }

    private void setupGUI() {
        frame = new JFrame("Tic Tac Toe");
        frame.setSize(600, 550);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitGame();
            }
        });
        frame.setLayout(new BorderLayout());

        // Menu
        JMenuBar menuBar = new JMenuBar();
        JMenu control = new JMenu("Control");
        JMenu help = new JMenu("Help");
        JMenuItem exit = new JMenuItem("Exit");
        JMenuItem instruction = new JMenuItem("Instruction");
        exit.addActionListener(e -> exitGame());
        instruction.addActionListener(e -> showInstructions());
        control.add(exit);
        help.add(instruction);
        menuBar.add(control);
        menuBar.add(help);
        frame.setJMenuBar(menuBar);

        // Top message
        messageLabel = new JLabel("Connecting to server...", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Arial", Font.BOLD, 16));
        frame.add(messageLabel, BorderLayout.NORTH);

        // Center board
        JPanel boardPanel = new JPanel(new GridLayout(3, 3, 5, 5));
        boardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j] = new JButton("");
                buttons[i][j].setFont(new Font("Arial", Font.BOLD, 80));
                buttons[i][j].setFocusPainted(false);
                buttons[i][j].addActionListener(this);
                boardPanel.add(buttons[i][j]);
            }
        }

        // Right score panel
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(180, 0));
        JLabel scoreTitle = new JLabel("Score", SwingConstants.CENTER);
        scoreTitle.setFont(new Font("Arial", Font.BOLD, 18));
        rightPanel.add(scoreTitle, BorderLayout.NORTH);

        JPanel scores = new JPanel(new GridLayout(3, 1, 0, 20));
        scores.setBorder(BorderFactory.createEmptyBorder(0, 10, 20, 10));
        p1Label = new JLabel("Player 1 Wins: 0", SwingConstants.CENTER);
        p2Label = new JLabel("Player 2 Wins: 0", SwingConstants.CENTER);
        drawLabel = new JLabel("Draws: 0", SwingConstants.CENTER);
        scores.add(p1Label);
        scores.add(p2Label);
        scores.add(drawLabel);
        rightPanel.add(scores, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout());
        center.add(boardPanel, BorderLayout.CENTER);
        center.add(rightPanel, BorderLayout.EAST);
        frame.add(center, BorderLayout.CENTER);

        // Bottom
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setPreferredSize(new Dimension(0, 80));
        JPanel namePanel = new JPanel();
        namePanel.add(new JLabel("Name: "));
        nameField = new JTextField(15);
        submitButton = new JButton("Submit");
        submitButton.addActionListener(e -> submitName());
        namePanel.add(nameField);
        namePanel.add(submitButton);
        bottom.add(namePanel, BorderLayout.NORTH);

        timeLabel = new JLabel("", SwingConstants.CENTER);
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        bottom.add(timeLabel, BorderLayout.SOUTH);
        frame.add(bottom, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 12345);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                new Thread(() -> {
                    try {
                        GameMessage msg;
                        while ((msg = (GameMessage) in.readObject()) != null) {
                            handleMessage(msg);
                        }
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Connection lost."));
                    }
                }).start();

            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Cannot connect to server.");
                System.exit(0);
            }
        }).start();
    }

    private void handleMessage(GameMessage msg) {
        SwingUtilities.invokeLater(() -> {
            switch (msg.type) {
                case "START":
                    myMark = msg.p1Wins == 0 && msg.p2Wins == 0 && msg.draws == 0 ? "X" : (msg.p1Wins > msg.p2Wins ? "O" : "X");
                    updateScores(msg);
                    break;
                case "YOUR_TURN":
                    myTurn = true;
                    messageLabel.setText("Your turn!");
                    break;
                case "MESSAGE":
                    messageLabel.setText(msg.content);
                    break;
                case "MOVE":
                    String[] parts = msg.content.split(",");
                    int pos = Integer.parseInt(parts[0]);
                    String mark = parts[1];
                    int row = pos / 3, col = pos % 3;
                    buttons[row][col].setText(mark);
                    buttons[row][col].setForeground(mark.equals("X") ? Color.RED : Color.BLUE);
                    myTurn = false;
                    break;
                case "WIN":
                case "DRAW":
                case "OPPONENT_LEFT":
                    updateScores(msg);
                    messageLabel.setText(msg.content);
                    int option = JOptionPane.showConfirmDialog(frame,
                            msg.content + "\nDo you want to play again?",
                            "Game Over", JOptionPane.YES_NO_OPTION);
                    if (option == JOptionPane.YES_OPTION) {
                        send(new GameMessage("RESTART", null, 0, 0, 0));
                    } else {
                        exitGame();
                    }
                    break;
                case "BOTH_READY":
                    messageLabel.setText("WELCOME " + nameField.getText().trim());
                    break;
            }
        });
    }

    private void updateScores(GameMessage msg) {
        p1Label.setText("Player 1 Wins: " + msg.p1Wins);
        p2Label.setText("Player 2 Wins: " + msg.p2Wins);
        drawLabel.setText("Draws: " + msg.draws);
    }

    private void submitName() {
        if (nameSubmitted) return;
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;

        nameSubmitted = true;
        nameField.setEnabled(false);
        submitButton.setEnabled(false);
        frame.setTitle("Tic Tac Toe - Player: " + name);
        send(new GameMessage("NAME", name, 0, 0, 0));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!myTurn || !nameSubmitted) return;
        JButton btn = (JButton) e.getSource();
        if (!btn.getText().isEmpty()) return;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (buttons[i][j] == btn) {
                    send(new GameMessage("MOVE", String.valueOf(i * 3 + j), 0, 0, 0));
                    myTurn = false;
                    return;
                }
            }
        }
    }

    private void send(GameMessage msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (Exception ignored) {}
    }

    private void exitGame() {
        send(new GameMessage("EXIT", null, 0, 0, 0));
        System.exit(0);
    }

    private void showInstructions() {
        JOptionPane.showMessageDialog(frame,
                "Tic Tac Toe Instructions:\n" +
                "- Enter your name to start.\n" +
                "- Player 1 uses 'X' (red), Player 2 uses 'O' (blue).\n" +
                "- Player 1 starts first.\n" +
                "- Take turns placing your mark.\n" +
                "- First to get 3 in a row wins!",
                "Instruction", JOptionPane.INFORMATION_MESSAGE);
    }

    private void startClock() {
        clockTimer = new Timer(1000, e -> timeLabel.setText(
                "Current Time: " + new SimpleDateFormat("HH:mm:ss").format(new Date())));
        clockTimer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TicTacToeClient::new);
    }
}