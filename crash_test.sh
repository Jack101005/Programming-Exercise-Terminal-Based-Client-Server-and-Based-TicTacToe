#!/bin/bash

echo "Starting 10,000 quick connections to see where the server breaks..."

for i in {1..100000}
do
   # Use 'nc' (netcat) to touch the port and immediately disconnect.
   # This tricks the server into spawning a thread without needing 
   # 10,000 heavy Java Client instances running at once.
   nc -z localhost 3000 &

   # Safety valve: Pause a tiny bit every 200 requests so your OS 
   # doesn't completely lock up.
   if [ $((i % 200)) -eq 0 ]; then
       sleep 0.05
       echo "Sent $i connections..."
   fi
done

wait
echo "Done!"