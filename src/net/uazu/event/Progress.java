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
 * Progress class, which can be used to communicate progress of a
 * single operation to the status-display handler of the application.
 * Construct one as the operation starts, set its status message
 * initially and whenever necessary after that using {@link #update},
 * and when complete call {@link #done()}.  The Progress instance
 * cannot be reused.  Override {@link #cancel()} to handle a request
 * to cancel the operation from the GUI.
 *
 * <p>To receive these status updates, the application should watch
 * for ProgressEvent and display the active status strings of the
 * Process instances returned by {@link EventLoop#getProgress}
 */
public abstract class Progress {
   public final EventLoop eloop;
   
   /**
    * Current status string, never null
    */
   public String status;
   
   /**
    * Construct a Progress instance
    */
   public Progress(EventLoop eloop, String status) {
      this.eloop = eloop;
      this.status = status;
      eloop.addProgress(this);
   }
   
   /**
    * Change the status string.  May be called from a background
    * thread.
    */
   public void update(String status) {
      this.status = status;
      eloop.updateProgress(this);
   }
   
   /**
    * Mark this Progress instance as complete, and remove from the
    * active list.  May be called from a background thread.
    */
   public void done() {
      eloop.removeProgress(this);
   }
   
   /**
    * Override this to handle cancellation of the task from the user
    * interface.
    */
   public void cancel() {}
}
