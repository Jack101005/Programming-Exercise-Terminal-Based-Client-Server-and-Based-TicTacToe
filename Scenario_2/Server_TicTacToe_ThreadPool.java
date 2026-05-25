package Scenario_2;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Server_TicTacToe_ThreadPool {

    static AtomicInteger gameCounter = new AtomicInteger(0);  
    static AtomicInteger activeGames = new AtomicInteger(0);  

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(3000);
            System.out.println("=== TicTacToe Server (Scenario 2 – Fixed Thread Pool) ===");
            System.out.println("Server on port 3000. Maximum of 4 threads active at once.\n");

            ExecutorService pool = Executors.newFixedThreadPool(4);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { serverSocket.close(); } catch (IOException ignored) {}
                pool.shutdown();
                System.out.println("\n[Server] Shut down.");
            }));

            while (!serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    int gameId = gameCounter.incrementAndGet();
                    System.out.println("[Game " + gameId + "] Client connected from " + client.getInetAddress()
                            + "  |  Active games: " + (activeGames.get() + 1));

                    pool.execute(() -> {
                        activeGames.incrementAndGet();
                        try {
                            playGame(client, gameId);
                        } catch (Exception e) {
                            System.out.println("[Game " + gameId + "] Error: " + e.getMessage());
                        } finally {
                            try { client.close(); } catch (IOException ignored) {}
                            int remaining = activeGames.decrementAndGet();
                            System.out.println("[Game " + gameId + "] Finished.  |  Active games: " + remaining);
                        }
                    });

                } catch (IOException e) {
                    if (!serverSocket.isClosed()) e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void playGame(Socket con, int gameId) throws IOException {
        BufferedReader in  = new BufferedReader(new InputStreamReader(con.getInputStream()));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(con.getOutputStream()));
        String tag = "[Game " + gameId + "]";

        out.write("START\n");
        out.flush();

        char[] board = new char[9];
        Arrays.fill(board, '.');
        char serverSymbol = 'X';   
        char clientSymbol = 'O';   
        boolean serverTurn = true; 

        while (true) {

            if (serverTurn) {
                try { Thread.sleep(800); } catch (InterruptedException ignored) {} 

                int pos = randomMove(board);
                board[pos] = serverSymbol;
                out.write(pos + "\n");
                out.flush();
                System.out.println(tag + " Server plays → " + pos);
                printBoard(board, tag);

                if (checkWin(board, serverSymbol)) {
                    System.out.println(tag + " Server (X) wins!");
                    return;
                }
                if (isDraw(board)) {
                    System.out.println(tag + " It's a draw!");
                    return;
                }

            } else {
                String line = in.readLine();

                if (line == null || line.equalsIgnoreCase("q")) {
                    System.out.println(tag + " Client quit or disconnected.");
                    return;
                }

                int pos;
                try {
                    pos = Integer.parseInt(line.trim());
                } catch (NumberFormatException e) {
                    System.out.println(tag + " Invalid input from client: " + line);
                    return;
                }

                board[pos] = clientSymbol;
                System.out.println(tag + " Client plays → " + pos);
                printBoard(board, tag);

                if (checkWin(board, clientSymbol)) {
                    System.out.println(tag + " Client (O) wins!");
                    return;
                }
                if (isDraw(board)) {
                    System.out.println(tag + " It's a draw!");
                    return;
                }
            }

            serverTurn = !serverTurn;
        }
    }

    static int randomMove(char[] board) {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (board[i] == '.') empty.add(i);
        }
        return empty.get(ThreadLocalRandom.current().nextInt(empty.size()));
    }

    static void printBoard(char[] b, String tag) {
        System.out.println();
        for (int row = 0; row < 3; row++) {
            StringBuilder sb = new StringBuilder(tag + "  ");
            for (int col = 0; col < 3; col++) {
                int i = row * 3 + col;
                sb.append(b[i] == '.' ? String.valueOf(i) : b[i]);
                if (col < 2) sb.append(" | ");
            }
            System.out.println(sb);
            if (row < 2) System.out.println(tag + "  ---+---+---");
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