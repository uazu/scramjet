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

/**
 * Represents a chain of event handlers.  Handlers may be added or
 * deleted using {@link #add} and {@link #del}.  Events may be passed
 * to the chain for processing using {@link #pass}.
 */
public class EventChain {
   private LinkedList<EventHandler> handlers = new LinkedList<EventHandler>();
   private ArrayList<Event> input = new ArrayList<Event>();
   private ArrayList<Event> output = new ArrayList<Event>();
   
   /**
    * Constructor.
    */
   public EventChain() {
      // Nothing to do
   }
  
   /**
    * Register an EventHandler.  This gets added at the end of the
    * list.
    */
   public void add(EventHandler h) {
      handlers.addLast(h);
   }

   /**
    * Unregister an EventHandler.  This gets removed from the list.
    */
   public void del(EventHandler h) {
      handlers.remove(h);
   }

   /**
    * Is the list of handlers empty?
    */
   public boolean isEmpty() {
      return handlers.isEmpty();
   }
   
   /**
    * Pass an event forward to the next handler.  This must only be
    * called from a handler, or else the event is lost.
    */
   public void pass(Event ev) {
      output.add(ev);
   }
   
   /**
    * Process an event through the whole chain of handlers.  This
    * assumes that only one process() call is active at any one time.
    */
   public void process(Event ev) {
      input.clear();
      input.add(ev);
      
      for (EventHandler handler : handlers) {
         output.clear();
         for (Event ev2 : input)
            handler.pass(ev2, output);
         ArrayList<Event> tmp = input; input = output; output = tmp;
      }
      input.clear();
      output.clear();
   }
}
