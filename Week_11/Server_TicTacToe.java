package Week_11;

import com.sun.net.httpserver.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Scenario 4 — Stateless REST Server with signed clientID.
 *
 * The clientID is NOT a username — it is a signed token that contains the
 * full game state (board + move number). The server signs it so the client
 * cannot forge or modify it. Every move, the server returns a new clientID
 * representing the new state.
 *
 * To "cheat" (skip moves and jump straight to winning), the cheater would
 * need a clientID for a board one move away from winning. But the only
 * clientIDs they have are the ones the server actually issued during their
 * real game, so they cannot skip ahead.
 *
 * The server keeps ZERO per-game memory. It only holds the SECRET.
 */
public class Server_TicTacToe {

    // The secret used to sign every clientID. This is the only thing the
    // server remembers between requests. Without it the client cannot
    // forge a clientID.
    static final byte[] SECRET = "my-tictactoe-secret-key-2026".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/game/new",  Server_TicTacToe::handleNew);
        server.createContext("/game/move", Server_TicTacToe::handleMove);

        server.setExecutor(null); // built-in dispatch — no threads written by us
        server.start();

        System.out.println("=== Stateless TicTacToe REST Server (Scenario 4) ===");
        System.out.println("Running on http://localhost:8080\n");
        System.out.println("Endpoints:");
        System.out.println("  POST /game/new   - returns starting board + clientID");
        System.out.println("  POST /game/move  - body: clientID=...&move=4\n");
    }

    // ----------------------------------------------------------------------
    // POST /game/new
    // Returns an empty board and a clientID representing that empty board.
    // ----------------------------------------------------------------------
    static void handleNew(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, err("Method Not Allowed"));
            return;
        }
        char[] board = new char[9];
        Arrays.fill(board, '.');

        String clientID = makeClientID(new String(board), 0);

        System.out.println("[new] issued empty board, clientID=" + clientID.substring(0, 20) + "...");
        send(ex, 200, toJson(new String(board), clientID, "ongoing",
                "New game. You are X. Human plays first."));
    }

    // ----------------------------------------------------------------------
    // POST /game/move  - body: clientID=...&move=N
    //
    // The clientID contains the entire game state (board + move count +
    // signature). The server unpacks it, verifies the signature, validates
    // the move, applies it, makes the computer's move, then returns a NEW
    // clientID representing the new state.
    // ----------------------------------------------------------------------
    static void handleMove(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, err("Method Not Allowed"));
            return;
        }

        Map<String, String> p = readBody(ex);
        String clientID = p.get("clientID");
        String moveStr  = p.get("move");

        if (clientID == null || moveStr == null) {
            send(ex, 400, err("Missing clientID or move")); return;
        }

        int move;
        try { move = Integer.parseInt(moveStr.trim()); }
        catch (NumberFormatException e) { send(ex, 400, err("move must be integer")); return; }

        // ── Unpack the clientID into its parts: board, moveNum, signature ──
        String[] parts = clientID.split("\\|");
        if (parts.length != 3) {
            System.out.println("[move] REJECTED: malformed clientID");
            send(ex, 400, err("Malformed clientID")); return;
        }
        String board    = parts[0];
        String numStr   = parts[1];
        String sig      = parts[2];

        int moveNum;
        try { moveNum = Integer.parseInt(numStr); }
        catch (NumberFormatException e) { send(ex, 400, err("Bad moveNum in clientID")); return; }

        // ── ANTI-CHEAT 1: signature check ──
        // Recompute what the signature should be for this board+moveNum.
        // If the client modified the clientID, the signatures won't match.
        String expectedSig = sign(board, moveNum);
        if (!expectedSig.equals(sig)) {
            System.out.println("[move] REJECTED: signature mismatch (cheat attempt)");
            send(ex, 400, err("Invalid clientID - signature does not match"));
            return;
        }

        if (board.length() != 9) {
            send(ex, 400, err("Board must be 9 characters")); return;
        }
        char[] b = board.toCharArray();

        // ── ANTI-CHEAT 2: reachability check ──
        // Even if the signature is somehow valid, the board itself must be
        // reachable from a real game (X count == O count when human is about
        // to play, no illegal characters).
        int xCount = 0, oCount = 0;
        for (char c : b) {
            if (c == 'X') xCount++;
            else if (c == 'O') oCount++;
            else if (c != '.') { send(ex, 400, err("Invalid board character: " + c)); return; }
        }
        if (xCount != oCount) {
            System.out.println("[move] REJECTED: unreachable board");
            send(ex, 400, err("Unreachable board state")); return;
        }
        if (xCount + oCount != moveNum) {
            send(ex, 400, err("moveNum does not match board")); return;
        }

        // ── ANTI-CHEAT 3: already-won check ──
        // The board sent must not already be a winning board — if it is,
        // the game already ended and no more moves are allowed.
        if (checkWin(b, 'X')) { send(ex, 400, err("Game already won by X")); return; }
        if (checkWin(b, 'O')) { send(ex, 400, err("Game already won by O")); return; }

        // ── Validate the actual move ──
        if (move < 0 || move > 8) { send(ex, 400, err("Move out of range 0-8")); return; }
        if (b[move] != '.')       { send(ex, 400, err("Cell " + move + " is occupied")); return; }

        // ── Apply human move ──
        b[move] = 'X';
        System.out.println("[move] human played " + move);

        if (checkWin(b, 'X')) {
            String newBoard = new String(b);
            String newClientID = makeClientID(newBoard, moveNum + 1);
            send(ex, 200, toJson(newBoard, newClientID, "human_won", "You win!"));
            return;
        }
        if (isFull(b)) {
            String newBoard = new String(b);
            String newClientID = makeClientID(newBoard, moveNum + 1);
            send(ex, 200, toJson(newBoard, newClientID, "draw", "It is a draw!"));
            return;
        }

        // ── Apply computer move (first empty cell) ──
        int compMove = firstEmpty(b);
        b[compMove] = 'O';
        System.out.println("[move] computer played " + compMove);

        String newBoard = new String(b);
        int newMoveNum = moveNum + 2;
        String newClientID = makeClientID(newBoard, newMoveNum);

        if (checkWin(b, 'O')) {
            send(ex, 200, toJson(newBoard, newClientID, "computer_won",
                    "Computer played at " + compMove + ". Computer wins!"));
            return;
        }
        if (isFull(b)) {
            send(ex, 200, toJson(newBoard, newClientID, "draw",
                    "Computer played at " + compMove + ". It is a draw!"));
            return;
        }

        send(ex, 200, toJson(newBoard, newClientID, "ongoing",
                "Computer played at " + compMove + ". Your turn!"));
    }

    // ----------------------------------------------------------------------
    // Build a clientID by combining board, moveNum, and signature into
    // one opaque string. Format:  board|moveNum|signature
    // ----------------------------------------------------------------------
    static String makeClientID(String board, int moveNum) {
        return board + "|" + moveNum + "|" + sign(board, moveNum);
    }

    // ----------------------------------------------------------------------
    // HMAC-SHA256 signing — the magic that prevents forgery.
    // ----------------------------------------------------------------------
    static String sign(String board, int moveNum) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            String payload = board + "|" + moveNum;
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte by : hash) hex.append(String.format("%02x", by));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ----------------------------------------------------------------------
    // Game logic helpers
    // ----------------------------------------------------------------------
    static int firstEmpty(char[] b) {
        for (int i = 0; i < 9; i++) if (b[i] == '.') return i;
        return -1;
    }

    static boolean checkWin(char[] b, char s) {
        int[][] lines = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] l : lines) if (b[l[0]]==s && b[l[1]]==s && b[l[2]]==s) return true;
        return false;
    }

    static boolean isFull(char[] b) {
        for (char c : b) if (c == '.') return false;
        return true;
    }

    // ----------------------------------------------------------------------
    // HTTP helpers
    // ----------------------------------------------------------------------
    static String toJson(String board, String clientID, String status, String message) {
        return String.format(
            "{\"board\":\"%s\",\"clientID\":\"%s\",\"status\":\"%s\",\"message\":\"%s\"}",
            board, clientID, status, message);
    }

    static String err(String msg) {
        return "{\"error\":\"" + msg + "\"}";
    }

    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static Map<String, String> readBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[1024];
        int n;
        while ((n = ex.getRequestBody().read(tmp)) != -1) buf.write(tmp, 0, n);
        String raw = buf.toString(StandardCharsets.UTF_8);

        Map<String, String> map = new HashMap<>();
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2)
                map.put(URLDecoder.decode(kv[0], "UTF-8"),
                        URLDecoder.decode(kv[1], "UTF-8"));
        }
        return map;
    }
}