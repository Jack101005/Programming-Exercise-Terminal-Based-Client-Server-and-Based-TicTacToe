import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Server_TicTacToe {

    static BlockingQueue<Socket> waitingClients = new LinkedBlockingQueue<>();
    static AtomicBoolean gameInProgress = new AtomicBoolean(false);
    static Scanner keyboard = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(3000);
            System.out.println("=== TicTacToe Server started on port 3000. Always running... ===\n");

            // Gracefully close the server socket on Ctrl+C / JVM shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { serverSocket.close(); } catch (IOException ignored) {}
                System.out.println("\n[Server] Shut down.");
            }));

            // Acceptor thread — keeps accepting new clients forever
            Thread acceptor = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        System.out.println("[Server] New client connected from " + client.getInetAddress());

                        // If a game is running or others are already waiting, tell them to wait
                        boolean shouldWait = gameInProgress.get() || !waitingClients.isEmpty();
                        if (shouldWait) {
                            try {
                                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
                                bw.write("WAIT\n");
                                bw.flush();
                                System.out.println("[Server] Client told to wait. Clients in queue: " + (waitingClients.size() + 1));
                            } catch (IOException e) {
                                // Client disconnected before we could even tell them to wait
                                try { client.close(); } catch (IOException ignored) {}
                                continue;
                            }
                        }
                        waitingClients.offer(client);

                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) e.printStackTrace();
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            // Main game loop — runs forever, one game at a time
            while (true) {
                System.out.println("[Server] Waiting for next player to connect...");
                Socket client = waitingClients.take(); // blocks until a client is available
                gameInProgress.set(true);
                System.out.println("[Server] Starting new game!\n");

                try {
                    playGame(client);
                } catch (Exception e) {
                    System.out.println("[Server] Game ended unexpectedly: " + e.getMessage());
                } finally {
                    try { client.close(); } catch (IOException ignored) {}
                    gameInProgress.set(false);
                    System.out.println("\n[Server] Game over. Clients still waiting: " + waitingClients.size() + "\n");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void playGame(Socket con) throws IOException {
        BufferedReader in  = new BufferedReader(new InputStreamReader(con.getInputStream()));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(con.getOutputStream()));

        // Tell the client the game is starting
        out.write("START\n");
        out.flush();

        char[] board = new char[9];
        Arrays.fill(board, '.');
        char mySymbol  = 'X';
        char oppSymbol = 'O';
        boolean myTurn = true; // Server (X) always goes first

        while (true) {
            printBoard(board);

            if (myTurn) {
                // ── Server's turn ──
                int pos = -1;
                while (true) {
                    System.out.print("Your move (0-8) or 'q' to quit: ");
                    String line = keyboard.nextLine().trim();

                    if (line.equalsIgnoreCase("q")) {
                        out.write("QUIT\n");
                        out.flush();
                        System.out.println("You quit the game.");
                        return;
                    }
                    try {
                        pos = Integer.parseInt(line);
                    } catch (NumberFormatException e) {
                        System.out.println("Not a number. Try again.");
                        continue;
                    }
                    if (pos < 0 || pos > 8) {
                        System.out.println("Out of range (0-8). Try again.");
                        continue;
                    }
                    if (board[pos] != '.') {
                        System.out.println("That cell is taken. Try again.");
                        continue;
                    }
                    break;
                }
                board[pos] = mySymbol;
                out.write(pos + "\n");
                out.flush();

                if (checkWin(board, mySymbol)) {
                    printBoard(board);
                    System.out.println("You win!");
                    return;
                }
                if (isDraw(board)) {
                    printBoard(board);
                    System.out.println("It's a draw!");
                    return;
                }

            } else {
                // ── Waiting for client's move ──
                System.out.println("Waiting for opponent's move...");
                String line = in.readLine();

                if (line == null || line.equalsIgnoreCase("q")) {
                    System.out.println("Opponent quit or disconnected.");
                    return;
                }

                int pos;
                try {
                    pos = Integer.parseInt(line.trim());
                } catch (NumberFormatException e) {
                    System.out.println("Received invalid input from client: " + line);
                    return;
                }
                board[pos] = oppSymbol;

                if (checkWin(board, oppSymbol)) {
                    printBoard(board);
                    System.out.println("You lose!");
                    return;
                }
                if (isDraw(board)) {
                    printBoard(board);
                    System.out.println("It's a draw!");
                    return;
                }
            }
            myTurn = !myTurn;
        }
    }

    static void printBoard(char[] b) {
        System.out.println();
        for (int row = 0; row < 3; row++) {
            StringBuilder sb = new StringBuilder(" ");
            for (int col = 0; col < 3; col++) {
                int i = row * 3 + col;
                sb.append(b[i] == '.' ? String.valueOf(i) : b[i]);
                if (col < 2) sb.append(" | ");
            }
            System.out.println(sb);
            if (row < 2) System.out.println("---+---+---");
        }
        System.out.println();
    }

    static boolean checkWin(char[] b, char sym) {
        int[][] lines = {
            {0,1,2}, {3,4,5}, {6,7,8},
            {0,3,6}, {1,4,7}, {2,5,8},
            {0,4,8}, {2,4,6}
        };
        for (int[] line : lines) {
            if (b[line[0]] == sym && b[line[1]] == sym && b[line[2]] == sym) return true;
        }
        return false;
    }

    static boolean isDraw(char[] b) {
        for (char c : b) if (c == '.') return false;
        return true;
    }
}
