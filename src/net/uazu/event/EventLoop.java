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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Event loop handler -- the {@link #loop()} call runs the main loop
 * of the application.  The main loop passes all events through a
 * chain of {@link EventHandler} instances for processing, which is
 * set up using {@link #addHandler} and {@link #delHandler}.
 *
 * <p>Events are added to the queue to be processed using {@link #add}
 * or {@link #addUrgent}.  This is often done from background threads
 * which monitor some input resource for new data, then add an event
 * to the queue.  This wakes the main loop to process it.
 * 
 * <p>There are several automatically-generated events.  {@link
 * ResizeEvent} is generated in response to one or more calls to
 * {@link #reqResize()}.  {@link UpdateEvent} is generated in response
 * to one or more calls to {@link #reqUpdate()}.  {@link
 * ProgressEvent} is generated when there is a change in the current
 * status messages maintained by {@link Progress} instances.  {@link
 * IdleEvent} is generated when there is nothing left to do, before
 * waiting for a new event to occur.
 *
 * <p>It is possible for a background thread to request a callback
 * within the main loop using {@link #reqCallback}, usually to do a
 * display update or similar.  Timers may be scheduled using {@link
 * #addTimer}.  The main loop may be terminated by calling {@link
 * #reqAbort}.
 */
public class EventLoop {
   /**
    * Constructor.
    */
   public EventLoop() {
      thread = Thread.currentThread();
   }

   /**
    * Thread which the main loop is running in
    */
   public final Thread thread;
   
   /**
    * Object to use to lock on.
    */
   private Object lock = new Object();

   // Final instances to save keeping reallocating them
   private final ResizeEvent RESIZE_EVENT = new ResizeEvent();
   private final UpdateEvent UPDATE_EVENT = new UpdateEvent();
   private final IdleEvent IDLE_EVENT = new IdleEvent();
   private final ProgressEvent PROGRESS_EVENT = new ProgressEvent();
   private final Event ABORT_EVENT = new Event();

   /**
    * Event handlers, in order.
    */
   private EventChain chain = new EventChain();

   /**
    * Events waiting to be processed.
    */
   private LinkedList<Event> queue = new LinkedList<Event>();

   /**
    * Urgent events waiting to be processed.
    */
   private LinkedList<Event> urgent = new LinkedList<Event>();

   /**
    * Callbacks waiting to be processed.
    */
   private LinkedList<Runnable> callbacks = new LinkedList<Runnable>();

   /**
    * Timer thread, or null if no timers have been started yet.
    */
   private TimerThread timer_thread;
   
   /**
    * Number of resize-requests registered.
    */
   private int resize_count = 0;

   /**
    * Number of resize-requests handled.
    */
   private int resize_chaser = 0;

   /**
    * Number of update-requests registered.
    */
   private int update_count = 0;

   /**
    * Number of update-requests handled.
    */
   private int update_chaser = 0;

   /**
    * Number of progress-changes registered.
    */
   private int progress_count = 0;

   /**
    * Number of progress-changes handled.
    */
   private int progress_chaser = 0;
   
   /**
    * Abort flag.
    */
   private boolean abort = false;

   /**
    * Running flag.
    */
   private boolean running = true;

   /**
    * Check whether main loop is still running.
    */
   public boolean running() {
      // Check thread.isAlive() because it might have crashed without
      // passing finally {} due to SIGPIPE
      return running && thread.isAlive();
   }
   
   /**
    * Add an event to the normal queue.  May be called from a
    * background thread.
    */
   public void add(Event ev) {
      synchronized(lock) {
         queue.addLast(ev);
         lock.notify();
      }
   }

   /**
    * Add a higher-priority event to the urgent queue, which gets
    * processed before the normal queue.  May be called from a
    * background thread.
    */
   public void addUrgent(Event ev) {
      synchronized(lock) {
         urgent.addLast(ev);
         lock.notify();
      }
   }

   /**
    * Request that a ResizeEvent be generated at the next opportunity.
    * May be called from a background thread.
    */
   public void reqResize() {
      synchronized(lock) {
         resize_count++;
         lock.notify();
      }
   }

   /**
    * Request that an UpdateEvent be generated at the next
    * opportunity.  This is not designed to be called from background
    * threads, only from handlers running in the main thread, as it
    * will not interrupt a waiting event thread.  Display changes
    * should not normally be made from background threads.
    */
   public void reqUpdate() {
      update_count++;
   }

   /**
    * Request a callback at the next opportunity.  May be called from
    * a background thread to request that its callback be run in the
    * main thread.
    */
   public void reqCallback(Runnable run) {
      synchronized(lock) {
         callbacks.addLast(run);
         lock.notify();
      }
   }

   /**
    * Request an abort of the event thread.  The {@link #loop} call
    * returns at the next opportunity.  May be called from a
    * background thread.
    */
   public void reqAbort() {
      synchronized(lock) {
         abort = true;
         lock.notify();
      }
   }

   /**
    * Add a timer which will be scheduled and called back in the main
    * loop soon after the requested time.  The timer may optionally
    * re-scheduled itself at that point.  See the callback in the
    * {@link Timer} class.
    */
   public void addTimer(Timer timer) {
      if (timer_thread == null) {
         timer_thread = new TimerThread(this);
         timer_thread.start();
      }
      timer_thread.add(timer);
   }

   /**
    * Register an EventHandler.  It gets added at the end of the
    * chain, which means that it will receive the event after all the
    * other handlers have tried to process it.  May be called from a
    * background thread.
    */
   public void addHandler(EventHandler h) {
      synchronized(lock) {
         chain.add(h);
      }
   }

   /**
    * Unregister an EventHandler, removing it from the chain.  May be
    * called from a background thread.
    */
   public void delHandler(EventHandler h) {
      synchronized(lock) {
         chain.del(h);
         if (chain.isEmpty())
            lock.notify();
      }
   }

   /**
    * Main event-loop.  Processes outstanding events in this order:
    * resizes first, then urgent events, then callbacks, then normal
    * events, then progress updates, then update events, then finally
    * the idle event before waiting for another event to occur.  If
    * any new events are generated whilst processing existing events,
    * they are handled according to the same priority order above,
    * i.e. an urgent event generated from an update event would get
    * processed before the idle event.  The main loop exits only when
    * there are no more handlers or after {@link #reqAbort}.
    */
   public void loop() {
      running = true;
      try {
         boolean send_idle = false;
         while (true) {
            // Wait for the next action to perform
            Object ev = nextAction(send_idle);
            send_idle = (ev != IDLE_EVENT);
            
            // Perform action
            if (ev == ABORT_EVENT) {
               break;
            }
            if (ev instanceof Event) {
               chain.process((Event) ev);
               continue;
            }
            if (ev instanceof Runnable) {
               ((Runnable) ev).run();
               continue;
            }
         }
      } finally {
         running = false;
      }
   }

   /**
    * Find the next action to perform, waiting if necessary.  Keep the
    * lock as short a time as possible, definitely not doing any
    * callbacks here, to avoid deadlocks.
    */
   private Object nextAction(boolean send_idle) {
      synchronized(lock) {
         while (true) {
            // Terminate the event loop
            if (abort || chain.isEmpty()) {
               return ABORT_EVENT;
            }
            
            // Handle resizes right away
            if (resize_count != resize_chaser) {
               resize_chaser = resize_count;
               return RESIZE_EVENT;
            }
            
            // Then urgent stuff
            if (!urgent.isEmpty()) {
               return urgent.removeFirst();
            }
            
            // Then callbacks
            if (!callbacks.isEmpty()) {
               return callbacks.removeFirst();
            }
            
            // Then normal events
            if (!queue.isEmpty()) {
               return queue.removeFirst();
            }
            
            // Then progress updates
            if (progress_count != progress_chaser) {
               progress_chaser = progress_count;
               return PROGRESS_EVENT;
            }
            
            // Then finally any updates that were flagged by the previous
            // processing
            if (update_count != update_chaser) {
               update_chaser = update_count;
               return UPDATE_EVENT;
            }
            
            // Send idle before waiting
            if (send_idle) {
               return IDLE_EVENT;
            }
            
            // Wait for a new event arrival notification
            try {
               lock.wait();
            } catch (InterruptedException e) {}
         }
      }
   }

   // ------------------------------------------------------------------------
   // Paste buffer handling
   //
   
   /**
    * Paste buffer.
    */
   public String clip = "";
   
   /**
    * Clear the paste buffer
    */
   public void clipClear() {
      clip = "";
   }

   /**
    * Append text to the paste buffer.
    */
   public void clipAppend(String str) {
      clip += str;
   }

   /**
    * Prepend text to the paste buffer.
    */
   public void clipPrepend(String str) {
      clip = str + clip;
   }

   /**
    * Replace the paste buffer text.
    */
   public void clipSet(String str) {
      clip = str;
   }

   // ------------------------------------------------------------------------
   // Progress handling
   //

   // package-private
   void addProgress(Progress pr) {
      synchronized(progress_list) {
         progress_list.add(pr);
         progress_update();
      }
   }

   // package-private
   void removeProgress(Progress pr) {
      synchronized(progress_list) {
         progress_list.remove(pr);
         progress_update();
      }
   }

   // package-private
   void updateProgress(Progress pr) {
      synchronized(progress_list) {
         progress_update();
      }
   }

   /**
    * Progress instances in creation order.
    */
   private List<Progress> progress_list = new LinkedList<Progress>();

   /**
    * Running timer, or null if there is none running.
    */
   private Timer progress_timer = null;

   /**
    * Send a ProgressEvent to prompt the application to update its
    * status display, after a short delay.  Should always be called
    * under synchronized(progress_list).
    */
   private void progress_update() {
      // Delay progress updates slightly, so that a very brief status
      // never appears on the screen
      if (progress_timer == null) {
         long now = System.currentTimeMillis();
         progress_timer = new Timer(now + 100) {
               public long run(long req, long now) {
                  synchronized(lock) {
                     if (progress_count == progress_chaser) {
                        progress_count++;
                        lock.notify();
                     }
                  }
                  synchronized(progress_list) {
                     progress_timer = null;
                  }
                  return 0;
               }
            };
         addTimer(progress_timer);
      }
   }

   /**
    * Get the list of active Progress instances (with status strings).
    * Returns empty list if there are none.
    */
   public List<Progress> getProgress() {
      synchronized(progress_list) {
         return new ArrayList<Progress>(progress_list);
      }
   }

   /**
    * Check whether there are any progress messages to display.
    */
   public boolean hasProgress() {
      return !progress_list.isEmpty();
   }   
}
