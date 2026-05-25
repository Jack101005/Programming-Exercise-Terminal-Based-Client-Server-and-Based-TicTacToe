package Scenario_3;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Scenario 3 — REST Client
 *
 * No persistent socket. Each action is an independent HTTP POST.
 * The server's move is returned in the same response as the client's move —
 * so there is no polling, no waiting, and no open connection between turns.
 */
public class Client_TicTacToe {

    static final String SERVER = "http://localhost:8080";
    static final Scanner keyboard = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        System.out.println("=== TicTacToe Client (Scenario 3 – REST) ===\n");

        // ── Step 1: Create a new game (server also makes its first move here) ──
        String res    = post(SERVER + "/game/new", "");
        String gameId = extract(res, "gameId");
        String board  = extract(res, "board");

        System.out.println("Game ID : " + gameId);
        System.out.println("You are O  |  Server is X  |  Server goes first\n");
        System.out.println(extract(res, "message"));
        printBoard(board.toCharArray());

        // ── Step 2: Game loop ─────────────────────────────────────────────────
        while (true) {
            if (!"ongoing".equals(extract(res, "status"))) break;

            // Get client's move
            int pos = -1;
            while (true) {
                System.out.print("Your move (0-8) or 'q' to quit: ");
                String line = keyboard.nextLine().trim();

                if (line.equalsIgnoreCase("q")) {
                    post(SERVER + "/game/quit", "gameId=" + gameId);
                    System.out.println("You quit the game.");
                    return;
                }
                try { pos = Integer.parseInt(line); }
                catch (NumberFormatException e) { System.out.println("Not a number. Try again."); continue; }
                if (pos < 0 || pos > 8)          { System.out.println("Out of range. Try again."); continue; }
                if (board.charAt(pos) != '.')     { System.out.println("Cell taken. Try again.");   continue; }
                break;
            }

            // Send move — server validates, applies it, then immediately makes its own
            // move, and returns everything in ONE response (stateless, no extra round trip)
            res   = post(SERVER + "/game/move", "gameId=" + gameId + "&pos=" + pos);
            board = extract(res, "board");

            printBoard(board.toCharArray());
            System.out.println(extract(res, "message"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
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

    // Generic HTTP POST — opens a fresh connection each call (stateless)
    static String post(String urlStr, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        if (!body.isEmpty()) conn.getOutputStream().write(body.getBytes("UTF-8"));

        InputStream is = conn.getResponseCode() < 400
                ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[1024]; int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toString("UTF-8");
    }

    // Minimal JSON field extractor — no external library needed
    static String extract(String json, String field) {
        String key = "\"" + field + "\":\"";
        int s = json.indexOf(key);
        if (s == -1) return "";
        s += key.length();
        int e = json.indexOf('"', s);
        return e == -1 ? "" : json.substring(s, e);
    }
}
