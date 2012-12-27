// Copyright (c) 2008-2012 Jim Peters, http://uazu.net
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.uazu.event;

import java.util.PriorityQueue;

/**
 * Timer thread, which makes sure that all the timers get called
 * correctly.  It shuts down when the EventLoop dies.
 */
public class TimerThread extends Thread {
   /**
    * Timers waiting to run, kept in order of scheduled time.
    */
   private PriorityQueue<Timer> timers = new PriorityQueue<Timer>();

   /**
    * Lock for thread-safe access.
    */
   private Object lock = new Object();

   /**
    * Minimum wait of 10ms to avoid runaway.
    */
   private static final long MINIMUM_WAIT = 10;

   /**
    * Time to wait when there is nothing going on.  This is the
    * maximum time that the EventLoop can be dead before we notice.
    */
   private static final long IDLE_WAIT = 5000;
   
   /**
    * Associated event loop.
    */
   private final EventLoop eloop;

   /**
    * Most recent idea of current time.  It is updated every iteration
    * of the timer loop.  It may be up to {@link #IDLE_WAIT} ms out of
    * date.  This is okay as it is only used to limit runaway timers.
    */
   private long now;

   /**
    * Constructor
    */
   public TimerThread(EventLoop eloop) {
      this.eloop = eloop;
      setDaemon(true);
   }
   
   /**
    * Add a timer to the list of timers ready to run.
    */
   public void add(Timer timer) {
      synchronized(lock) {
         // 'now' may be out of date, but that's okay because this is
         // only to protect from runaway timer callbacks
         long min = now + MINIMUM_WAIT;   
         timer.time = timer.req < min ? min : timer.req;
         timers.add(timer);
         interrupt();   // Break the sleep so the TimerThread notices
      }
   }

   /**
    * Run the thread handler.
    */
   public void run() {
      while (eloop.running()) {
         now = System.currentTimeMillis();
         long wait = IDLE_WAIT;    // Check at least every 5 seconds that eloop is still running
         synchronized(lock) {
            final Timer timer = timers.peek();
            if (timer != null) {
               if (timer.time <= now) {
                  timers.poll();
                  final long then = now;
                  eloop.reqCallback(new Runnable() {
                        public void run() {
                           // 'then' may be out of date, but that's okay
                           long req = timer.run(timer.req, then);
                           if (req != 0) {
                              timer.req = req;
                              TimerThread.this.add(timer);
                           }
                        }
                     });
                  continue;
               }
               wait = timer.time - now;
               if (wait > IDLE_WAIT)
                  wait = IDLE_WAIT;
            }
         }
         try {
            sleep(wait);
         } catch (InterruptedException e) {}
      }
   }
}
