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
 * Timer class, which should be subclassed by a specific timer
 * instance.
 */
public abstract class Timer implements Comparable<Timer> {
   /**
    * Requested time.
    */
   long req;
   
   /**
    * Actual time it is scheduled to be called.
    */
   long time;

   /**
    * Construct with a requested absolute callback time.  This should
    * be after System.currentTimeMillis().
    */
   public Timer(long callback) {
      req = time = callback;
   }

   /**
    * Callback routine which must be implemented by the specific
    * timer.  The callback is made in the main thread.  The requested
    * callback time (req) and the actual callback time (now) are
    * passed.  The routine should return 0 if it doesn't want to be
    * called again, or else the time to be called.
    */
   public abstract long run(long req, long now);

   /**
    * Orders Timer instances by scheduled time.
    */
   public int compareTo(Timer b) {
      return (int) (b.time - time);
   }
}
