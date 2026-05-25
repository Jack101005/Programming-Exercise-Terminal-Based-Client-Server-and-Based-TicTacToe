package Scenario_3;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Scenario 3 — REST + Stateless Server
 *
 * No Thread / ThreadPool written by us.
 * Java's built-in HttpServer handles request dispatch internally.
 *
 * API endpoints:
 *   POST /game/new          → create a game; server makes first move automatically
 *   POST /game/move         → body: gameId=xxx&pos=4
 *   POST /game/quit         → body: gameId=xxx
 *
 * Each HTTP request is fully self-contained (stateless per request).
 * Game state lives in the in-memory ConcurrentHashMap below, looked up by gameId.
 */
public class Server_TicTacToe {

    // All active games — gameId → GameState
    static final ConcurrentHashMap<String, GameState> games = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/game/new",  Server_TicTacToe::handleNew);
        server.createContext("/game/move", Server_TicTacToe::handleMove);
        server.createContext("/game/quit", Server_TicTacToe::handleQuit);

        // No Thread / ThreadPool written by us — HttpServer manages dispatch
        server.setExecutor(null);
        server.start();

        System.out.println("=== TicTacToe REST Server (Scenario 3) ===");
        System.out.println("Running on http://localhost:8080\n");
        System.out.println("Endpoints:");
        System.out.println("  POST /game/new         — start a new game");
        System.out.println("  POST /game/move        — body: gameId=xxx&pos=4");
        System.out.println("  POST /game/quit        — body: gameId=xxx\n");
    }

    // ── POST /game/new ────────────────────────────────────────────────────────
    static void handleNew(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, err("Method Not Allowed")); return; }

        String gameId = UUID.randomUUID().toString().substring(0, 8);
        char[] board  = new char[9];
        Arrays.fill(board, '.');

        // Server (X) always goes first — make automated move immediately
        int first = randomMove(board);
        board[first] = 'X';

        games.put(gameId, new GameState(board, "ongoing"));

        System.out.println("[" + gameId + "] New game. Server played → " + first);
        send(ex, 200, toJson(gameId, board, "ongoing",
                "Server played at " + first + ". Your turn!"));
    }

    // ── POST /game/move  (body: gameId=xxx&pos=4) ─────────────────────────────
    static void handleMove(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, err("Method Not Allowed")); return; }

        Map<String, String> p = readBody(ex);
        String gameId = p.get("gameId");
        String posStr  = p.get("pos");

        if (gameId == null || posStr == null) { send(ex, 400, err("Missing gameId or pos")); return; }

        GameState gs = games.get(gameId);
        if (gs == null)                   { send(ex, 404, err("Game not found")); return; }
        if (!"ongoing".equals(gs.status)) {
            send(ex, 400, toJson(gameId, gs.board, gs.status, "Game is already over: " + gs.status));
            return;
        }

        int pos;
        try { pos = Integer.parseInt(posStr.trim()); }
        catch (NumberFormatException e) { send(ex, 400, err("Position must be a number 0-8")); return; }

        if (pos < 0 || pos > 8 || gs.board[pos] != '.') {
            send(ex, 400, err("Invalid move at position " + pos));
            return;
        }

        // ── Apply client's move (O) ──
        gs.board[pos] = 'O';
        System.out.println("[" + gameId + "] Client (O) → " + pos);

        if (checkWin(gs.board, 'O')) {
            gs.status = "client_win";
            send(ex, 200, toJson(gameId, gs.board, gs.status, "You win!"));
            return;
        }
        if (isDraw(gs.board)) {
            gs.status = "draw";
            send(ex, 200, toJson(gameId, gs.board, gs.status, "It's a draw!"));
            return;
        }

        // ── Server immediately responds with its own automated move (X) ──
        // Both moves happen inside ONE request/response — no extra round trip needed
        int srv = randomMove(gs.board);
        gs.board[srv] = 'X';
        System.out.println("[" + gameId + "] Server (X) → " + srv);

        if (checkWin(gs.board, 'X')) {
            gs.status = "server_win";
            send(ex, 200, toJson(gameId, gs.board, gs.status,
                    "Server played at " + srv + ". Server wins!"));
            return;
        }
        if (isDraw(gs.board)) {
            gs.status = "draw";
            send(ex, 200, toJson(gameId, gs.board, gs.status,
                    "Server played at " + srv + ". It's a draw!"));
            return;
        }

        send(ex, 200, toJson(gameId, gs.board, "ongoing",
                "Server played at " + srv + ". Your turn!"));
    }

    // ── POST /game/quit  (body: gameId=xxx) ──────────────────────────────────
    static void handleQuit(HttpExchange ex) throws IOException {
        Map<String, String> p = readBody(ex);
        String gameId = p.get("gameId");
        if (gameId == null || !games.containsKey(gameId)) { send(ex, 404, err("Game not found")); return; }
        games.remove(gameId);
        System.out.println("[" + gameId + "] Client quit. Game removed.");
        send(ex, 200, "{\"status\":\"quit\",\"message\":\"You quit the game.\"}");
    }

    // ── Game logic ────────────────────────────────────────────────────────────
    static int randomMove(char[] b) {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (b[i] == '.') empty.add(i);
        return empty.get(new Random().nextInt(empty.size()));
    }

    static boolean checkWin(char[] b, char s) {
        int[][] lines = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] l : lines) if (b[l[0]]==s && b[l[1]]==s && b[l[2]]==s) return true;
        return false;
    }

    static boolean isDraw(char[] b) {
        for (char c : b) if (c == '.') return false;
        return true;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────
    static String toJson(String id, char[] board, String status, String msg) {
        return String.format(
            "{\"gameId\":\"%s\",\"board\":\"%s\",\"status\":\"%s\",\"message\":\"%s\"}",
            id, new String(board), status, msg);
    }

    static String err(String msg) {
        return "{\"error\":\"" + msg + "\"}";
    }

    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static Map<String, String> readBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[1024];
        int n;
        while ((n = ex.getRequestBody().read(tmp)) != -1) buf.write(tmp, 0, n);
        String raw = buf.toString("UTF-8");

        Map<String, String> map = new HashMap<>();
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2)
                map.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
        }
        return map;
    }

    // ── Game state (stored server-side, looked up by gameId each request) ─────
    static class GameState {
        char[] board;
        String status; // "ongoing" | "client_win" | "server_win" | "draw"

        GameState(char[] board, String status) {
            this.board  = board;
            this.status = status;
        }
    }
}
