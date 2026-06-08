package Week_11;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Scenario 4 — Client for stateless server.
 *
 * The client carries one clientID between requests. Every move:
 *   - Send the current clientID + the chosen move
 *   - Get back a new clientID representing the updated state
 *
 * The client cannot cheat because forging a clientID requires the server's
 * secret key.
 */
public class Client_TicTacToe {

    static final String SERVER = "http://localhost:8080";
    static final Scanner keyboard = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        System.out.println("=== TicTacToe Client (Scenario 4 – Stateless Server) ===\n");

        // Step 1: ask the server for a new game
        String res = post(SERVER + "/game/new", "");

        String board    = extract(res, "board");
        String clientID = extract(res, "clientID");
        String status   = extract(res, "status");

        System.out.println(extract(res, "message"));
        printBoard(board.toCharArray());

        // Step 2: play moves until someone wins or it's a draw
        while ("ongoing".equals(status)) {

            int pos = -1;
            while (true) {
                System.out.print("Your move (0-8) or 'q' to quit: ");
                String line = keyboard.nextLine().trim();

                if (line.equalsIgnoreCase("q")) {
                    System.out.println("You quit the game.");
                    return;
                }
                try { pos = Integer.parseInt(line); }
                catch (NumberFormatException e) { System.out.println("Not a number. Try again."); continue; }
                if (pos < 0 || pos > 8)        { System.out.println("Out of range. Try again."); continue; }
                if (board.charAt(pos) != '.')  { System.out.println("Cell taken. Try again.");   continue; }
                break;
            }

            // Send the clientID + chosen move
            String body = "clientID=" + URLEncoder.encode(clientID, "UTF-8")
                        + "&move="    + pos;

            res = post(SERVER + "/game/move", body);

            // Update local state from the response — new clientID for next move
            board    = extract(res, "board");
            clientID = extract(res, "clientID");
            status   = extract(res, "status");

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

    static String post(String urlStr, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        if (!body.isEmpty()) conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        InputStream is = conn.getResponseCode() < 400
                ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[1024]; int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toString(StandardCharsets.UTF_8);
    }

    static String extract(String json, String field) {
        String key = "\"" + field + "\":\"";
        int s = json.indexOf(key);
        if (s == -1) return "";
        s += key.length();
        int e = json.indexOf('"', s);
        return e == -1 ? "" : json.substring(s, e);
    }
}