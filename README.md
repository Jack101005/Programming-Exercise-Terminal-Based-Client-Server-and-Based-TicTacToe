# Programming-Exercise-Terminal-Based-Client-Server-and-Based-TicTacToe

Scenario 1

Compiling code: javac Scenario_1/Server_TicTacToe.java Scenario_1/Client_TicTacToe.java

# From project root
java Scenario_1.Server_TicTacToe
java Scenario_1.Client_TicTacToe

Scenario 2

<<<<<<< Updated upstream
Compiling code: javac Scenario_1/Server_TicTacToe.java Scenario_1/Client_TicTacToe.java
                javac Scenario_2/Server_TicTacToe.java Scenario_2/Client_TicTacToe.java
=======
Compiling code: 
javac Scenario_1/Server_TicTacToe.java Scenario_1/Client_TicTacToe.java
javac Scenario_2/Server_TicTacToe.java Scenario_2/Client_TicTacToe.java
>>>>>>> Stashed changes


# From project root
java Scenario_2.Server_TicTacToe
java Scenario_2.Client_TicTacToe

-----------------------------------------------------------------------------------------------

Difference 1 — How the server handles each client
Scenario 1 (Server_TicTacToe.java:59):


Socket client = waitingClients.take(); // blocks — one game at a time
playGame(client);                      // next client must wait
One game runs, finishes, then the next client gets in. That's the queue.

Scenario 2 (Server_TicTacToe.java:33-47):


Thread gameThread = new Thread(() -> {
    playGame(client, gameId);   // runs in its OWN thread
});
gameThread.start();             // fires immediately, doesn't block the loop
Every client gets their own thread and starts instantly. This is the only structural change that removes the waiting.

-------------------------------------------------------------------------------------------------


Difference 2 — How the server plays its moves
Scenario 1 (Server_TicTacToe.java:101) — human types moves:


String line = keyboard.nextLine().trim(); // waits for human to type
Scenario 2 (Server_TicTacToe.java:81) — automated random moves:


int pos = randomMove(board); // picks a random empty cell automatically
The server had to become automated because one human can't type moves for 10 simultaneous games.

-------------------------------------------------------------------------------------------------

Scenario 3

Compiling code: javac Scenario_3/Server_TicTacToe.java Scenario_3/Client_TicTacToe.java 2>&1

# From project root
java Scenario_3.Server_TicTacToe
java Scenario_3.Client_TicTacToe
<<<<<<< Updated upstream
=======

-------------------------------------------------------------------------------------------------

crash_test.sh

try compile and run the server on scenario 2 first and then open a second terminal window and run the DoD file (Crash_test.sh)

Give Permission Code: chmod +x crash_test.sh

Run code: ./crash_test.sh

>>>>>>> Stashed changes
