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
 * BGTask handler.  Runs code first in a background thread and then in
 * the foreground thread.
 */
public abstract class BGTask extends Thread {
   private final EventLoop eloop;
   private boolean started = false;
   private Exception exc = null;
   private Progress status;
   private final String init_status;

   /**
    * Set true if cancellation of the operation has been requested.
    * The back() code may choose to check this and finish early.
    */
   public volatile boolean cancelled = false;

   /**
    * Set true once background execution has completed (once status
    * has been cleared).
    */
   public boolean completed = false;
   
   /**
    * Construct a BGTask.  Needs to be started separately with {@link
    * #start()}.
    */
   public BGTask(EventLoop eloop, String status) {
      super(status);
      this.eloop = eloop;
      init_status = status;
   }

   /**
    * Update the status string.  This only has an effect whilst the
    * back() call is running.
    */
   public void status(String status_str) {
      if (status != null)
         status.update(status_str);
   }

   /**
    * Code to run in background.  If it returns normally, then fore()
    * is run next.  If it throws an exception then fail() is run
    * next.
    */
   public abstract void back() throws Exception;

   /**
    * Code to run in foreground.  If it throws an exception then
    * fail() is run.
    */
   public abstract void fore() throws Exception;

   /**
    * Code to run in foreground in case of exception.
    */
   public abstract void fail(Exception e);

   /**
    * Cancellation of the operation was requested by the user.  This
    * method may be be overridden by subclasses.  The {@link
    * #cancelled} flag is set in any case before calling this method,
    * so this only needs to be overridden if some other action needs
    * to be taken to handle cancellation.  Default implemention does
    * nothing.
    */
   public void req_cancel() {}

   /**
    * Cancel this BGTask if possible.  The {@link #cancelled} flag is
    * set and {@link #req_cancel()} is called.  Whether the BGTask
    * responds to this or not depends on the implementation.
    */
   public final void cancel() {
      cancelled = true;
      req_cancel();
   }
   
   /**
    * Implementation of Thread.run() and Runnable.run().  Used for
    * both background thread and foreground thread.
    */
   public final void run() {
      if (!started) {
         // In background thread
         started = true;
         status = new Progress(eloop, init_status) {
               public void cancel() {
                  BGTask.this.cancel();
               }
            };
         try {
            try {
               back();
            } catch (Exception e) {
               exc = e;
            }
         } finally {
            status.done();
            status = null;
            completed = true;
         }
         // Pass control to foreground
         eloop.reqCallback(this);
      } else {
         // In foreground thread
         if (exc == null) {
            try {
               fore();
            } catch (Exception e) {
               exc = e;
            }
         }
         if (exc != null) {
            fail(exc);
         }
      }
   }
}

// END //
