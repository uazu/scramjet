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

package net.uazu.con;

import net.uazu.event.Event;

/**
 * KeyEvent from the keyboard, representing a keypress.
 */
public class KeyEvent extends Event {
   /**
    * Construct a KeyEvent for a key that generates a printable
    * character, either with or without meta.
    */
   public KeyEvent(int ch, boolean meta) {
      tag = meta ? KP.M_KEY : KP.KEY;
      key = (char) ch;
   }

   /**
    * Construct a KeyEvent for a key that doesn't contain a printable
    * character.
    */
   public KeyEvent(KP tag) {
      this.tag = tag;
      key = (char) 0;
   }

   /**
    * Construct a KeyEvent for a key that that has a printable
    * character connected.
    */
   public KeyEvent(KP tag, int key) {
      this.tag = tag;
      this.key = (char) key;
   }

   /**
    * Tag for key.  See {@link KP}.
    */
   public final KP tag;

   /**
    * Printable character typed, or 0 if not printable.
    */
   public final char key;

   /**
    * Debugging string.
    */
   public String toString() {
      return "KeyEvent " + tag + ":" + key;
   }
}

// END //
