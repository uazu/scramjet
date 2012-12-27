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

/**
 * DelayAction which executes a callback a set time after the most
 * recent trigger.  For example, this can be used to execute something
 * (a check or update for example) in a delay in typing.  Each
 * keypress would generate a {@link #trigger} call, then 300ms after
 * the last trigger, the callback runs.
 */
public abstract class DelayAction {
   /**
    * Delay before running callback.
    */
   private int delay;

   /**
    * Event loop
    */
   private final EventLoop eloop;
   
   /**
    * Timer or null.
    */
   private Timer timer;
   
   /**
    * Time that run() should be triggered.
    */
   private long trigger;
   
   /**
    * Construct with default delay (300ms).
    */
   public DelayAction(EventLoop eloop) {
      this(eloop, 300);
   }

   /**
    * Construct with given delay.
    */
   public DelayAction(EventLoop eloop, int delay) {
      this.eloop = eloop;
      this.delay = delay;
   }

   /**
    * Change the delay time.  Takes effect after the next trigger().
    */
   public void setDelay(int delay) {
      this.delay = delay;
   }
   
   /**
    * Trigger the timer to callback after 300ms, unless retriggered in
    * the meantime.
    */
   public void trigger() {
      synchronized(this) {
         trigger = System.currentTimeMillis() + delay;
         if (timer == null) {
            timer = new Timer(trigger) {
                  public long run(long req, long now) {
                     synchronized(this) {
                        if (trigger > now)
                           return trigger;
                        timer = null;
                     }
                     DelayAction.this.run();
                     return 0;
                  }
               };
            eloop.addTimer(timer);
         }
      }
   }

   /**
    * Callback which should be implemented by subclass.  Called 300ms
    * after the last trigger() call.
    */
   public abstract void run();
}
