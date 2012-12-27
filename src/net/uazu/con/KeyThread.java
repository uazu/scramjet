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

import java.io.IOException;
import java.io.InputStream;

import net.uazu.event.EventLoop;


/**
 * Monitor the keyboard and generate events.  An instance of this
 * class should be created and {@link #start()} should be called on
 * it.  It will then monitor STDIN and convert any incoming keypresses
 * into {@link KeyEvent} instances which will be delivered to the
 * {@link EventLoop} class.
 */
public class KeyThread extends Thread {
   private final Console con;
   private final EventLoop eloop;
   private byte[] buf = new byte[256];
   private int buf_index = 0;
   private int buf_len = 0;
   private IOException BADKEY = new IOException("Invalid key");
   private boolean raw = false;

   /**
    * Construct the thread.
    */
   public KeyThread(Console con, EventLoop eloop) {
      super("Keyboard input");
      setDaemon(true);
      this.con = con;
      this.eloop = eloop;
   }

   /**
    * Switch raw mode on/off.
    */
   public void rawMode(boolean on) {
      if (raw != on)
         con.tif.rawMode(on);
      raw = on;
   }

   /**
    * Implements superclass method.  This is called when the thread
    * is started.
    */
   public void run() {
      rawMode(true);

      try {
         InputStream in = con.tif.getInput();
         while (eloop.running()) {
            // Blocks until a character is available, then reads all
            // available (to make sure we get the whole stream for a
            // multi-byte key, e.g. F1 or Up)
            try {
               int ch = in.read();
               if (ch == -1) break;
               buf_len = 0;
               buf[buf_len++] = (byte) ch;
               int avail = in.available();
               for (int a = avail; a>0; a--) {
                  if (buf_len == buf.length) break;
                  ch = in.read();
                  if (ch == -1) break;
                  buf[buf_len++] = (byte) ch;
               }
               buf_index = 0;
            } catch (IOException e) {
               // Abort application on I/O error
               con.debug("I/O error on keyboard input: " + e.getMessage());
               break;
            }
            
            // Convert to key events
            try {
               while (more())
                  eloop.add(convertKey());
            } catch (IOException e) {
               // Throw away the rest of the buffer as it could be broken
               // sequences following this stuff we didn't understand
               con.beep();
            }
         }
      } finally {
         // May crash out in case of scramjet C front-end being killed
         // off.  Need to make sure that event loop thread also
         // terminates.
         eloop.reqAbort();
      }
   }

   private final int get() throws IOException {
      if (buf_index >= buf_len) throw BADKEY;
      return 255 & buf[buf_index++];
   }

   private final boolean more() {
      return buf_index < buf_len;
   }
    
   private KeyEvent convertKey() throws IOException {
      boolean meta = false;
      KP id = null;
      int key = 0;

      while (true) {
         int ch = get();

         switch (ch) {
         case 8: id = meta ? KP.M_C_H : KP.C_H; break;
         case 9: id = meta ? KP.M_Tab : KP.Tab; break;
         case 13: id = meta ? KP.M_Ret : KP.Ret; break;
         case 127: id = meta ? KP.M_BSp : KP.BSp; break;
         default:
            if (ch < 32) {
               id = meta ? KP.meta_ctrl_table[ch] : KP.ctrl_table[ch];
            } else if (ch < 127 || ch >= 160) {
               id =  meta ? (ch < 127 ? KP.meta_key_table[ch-32] : KP.M_KEY) : KP.KEY;
               key = ch;
            } else {
               throw BADKEY;
            }
            break;
         case 27:
            if (!more()) { id = meta ? KP.M_Esc : KP.Esc; break; }
            ch = get();
            switch (ch) {
            default: meta = true; buf_index--; continue;
            case 'O':
               ch = get();
               switch (ch) {
               case 'P': id = meta ? KP.M_F1 : KP.F1; break;
               case 'Q': id = meta ? KP.M_F2 : KP.F2; break;
               case 'R': id = meta ? KP.M_F3 : KP.F3; break;
               case 'S': id = meta ? KP.M_F4 : KP.F4; break;
               default: throw BADKEY;
               }
               break;
            case '[':
               ch = get();
               switch (ch) {
               case 'A': id = meta ? KP.M_Up : KP.Up; break;
               case 'B': id = meta ? KP.M_Down : KP.Down; break;
               case 'C': id = meta ? KP.M_Right : KP.Right; break;
               case 'D': id = meta ? KP.M_Left : KP.Left; break;
               case '[':
                  ch = get();
                  switch (ch) {
                  case 'A': id = meta ? KP.M_F1 : KP.F1; break;
                  case 'B': id = meta ? KP.M_F2 : KP.F2; break;
                  case 'C': id = meta ? KP.M_F3 : KP.F3; break;
                  case 'D': id = meta ? KP.M_F4 : KP.F4; break;
                  case 'E': id = meta ? KP.M_F5 : KP.F5; break;
                  default: throw BADKEY;
                  }
                  break;
               default:
                  if (ch >= '0' && ch <= '9') {
                     int val = ch - '0';
                     ch = get();
                     if (ch >= '0' && ch <= '9') {
                        val = val*10 + ch - '0';
                        ch = get();
                     }
                     if (ch != '~') throw BADKEY;
	      
                     switch (val) {
                     case 11: id = meta ? KP.M_F1 : KP.F1; break;
                     case 12: id = meta ? KP.M_F2 : KP.F2; break;
                     case 13: id = meta ? KP.M_F3 : KP.F3; break;
                     case 14: id = meta ? KP.M_F4 : KP.F4; break;
                     case 15: id = meta ? KP.M_F5 : KP.F5; break;
                     case 17: id = meta ? KP.M_F6 : KP.F6; break;
                     case 18: id = meta ? KP.M_F7 : KP.F7; break;
                     case 19: id = meta ? KP.M_F8 : KP.F8; break;
                     case 20: id = meta ? KP.M_F9 : KP.F9; break;
                     case 21: id = meta ? KP.M_F10 : KP.F10; break;
                     case 23: id = meta ? KP.M_F11 : KP.F11; break;
                     case 24: id = meta ? KP.M_F12 : KP.F12; break;
                     case 25: id = meta ? KP.M_F13 : KP.F13; break;
                     case 26: id = meta ? KP.M_F14 : KP.F14; break;
                     case 28: id = meta ? KP.M_F15 : KP.F15; break;
                     case 29: id = meta ? KP.M_F16 : KP.F16; break;
                     case 31: id = meta ? KP.M_F17 : KP.F17; break;
                     case 32: id = meta ? KP.M_F18 : KP.F18; break;
                     case 33: id = meta ? KP.M_F19 : KP.F19; break;
                     case 34: id = meta ? KP.M_F20 : KP.F20; break;
                     case 1: id = meta ? KP.M_Hom : KP.Hom; break;
                     case 2: id = meta ? KP.M_Ins : KP.Ins; break;
                     case 3: id = meta ? KP.M_Del : KP.Del; break;
                     case 4: id = meta ? KP.M_End : KP.End; break;
                     case 5: id = meta ? KP.M_PgU : KP.PgU; break;
                     case 6: id = meta ? KP.M_PgD : KP.PgD; break;
                     default: throw BADKEY;
                     }
                  } else {
                     throw BADKEY;
                  }
                  break;
               }
            }
         }

         return new KeyEvent(id, key);
      }
   }
}
	  
// END //
