import java.net.*;
import java.io.*;
import java.util.*;

public class Client_TicTacToe {

    public static void main(String[] args) {
        try {
            Socket s = new Socket("localhost", 3000);
            BufferedReader in  = new BufferedReader(new InputStreamReader(s.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
            Scanner keyboard  = new Scanner(System.in);

            // Read the first signal from the server
            String firstMsg = in.readLine();

            if ("WAIT".equals(firstMsg)) {
                System.out.println("Server is busy. You are in the queue — please wait...");
                // Block here until the server says it's our turn
                String msg;
                while ((msg = in.readLine()) != null) {
                    if ("START".equals(msg)) break;
                }
                System.out.println("It's your turn now!");
            }

            System.out.println("\nGame starting! You are O. Server (X) goes first.\n");

            char[] board = new char[9];
            Arrays.fill(board, '.');
            char mySymbol  = 'O';
            char oppSymbol = 'X';
            boolean myTurn = false; // Server (X) always goes first

            while (true) {
                printBoard(board);

                if (myTurn) {
                    // ── Client's turn ──
                    int pos = -1;
                    while (true) {
                        System.out.print("Your move (0-8) or 'q' to quit: ");
                        String line = keyboard.nextLine().trim();

                        if (line.equalsIgnoreCase("q")) {
                            out.write("q\n");
                            out.flush();
                            System.out.println("You quit the game.");
                            s.close();
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
                        break;
                    }
                    if (isDraw(board)) {
                        printBoard(board);
                        System.out.println("It's a draw!");
                        break;
                    }

                } else {
                    // ── Waiting for server's move ──
                    System.out.println("Waiting for opponent's move...");
                    String line = in.readLine();

                    if (line == null) {
                        System.out.println("Server disconnected.");
                        break;
                    }
                    if ("QUIT".equals(line)) {
                        System.out.println("Server quit the game.");
                        break;
                    }

                    int pos;
                    try {
                        pos = Integer.parseInt(line.trim());
                    } catch (NumberFormatException e) {
                        System.out.println("Received unexpected message: " + line);
                        break;
                    }
                    board[pos] = oppSymbol;

                    if (checkWin(board, oppSymbol)) {
                        printBoard(board);
                        System.out.println("You lose!");
                        break;
                    }
                    if (isDraw(board)) {
                        printBoard(board);
                        System.out.println("It's a draw!");
                        break;
                    }
                }
                myTurn = !myTurn;
            }

            s.close();
        } catch (Exception e) {
            e.printStackTrace();
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
