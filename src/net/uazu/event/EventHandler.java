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

import java.util.List;

/**
 * Interface for event handlers.  The event handler can choose to
 * process or ignore the event (doing 'return'), pass the event on
 * down the chain (doing 'out.add(ev)'), or map the event into one
 * or more other events and pass those on instead.
 */
public interface EventHandler {
   /**
    * Process the given event.
    */
   public void pass(Event ev, List<Event> out);
}
