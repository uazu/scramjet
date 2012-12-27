// Copyright (c) 2011-2012 Jim Peters, http://uazu.net
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

package net.uazu.scramjet;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * MsgReader, which handles reading in and parsing incoming messages.
 * Note that a caller must synchronize on this object from read()
 * right through to the successful match().
 */
public class MsgReader {
   public final InputStream in;
   public byte[] msg;
   public int msg_off;

   public MsgReader(InputStream in) {
      this.in = in;
   }

   private final byte in_read() throws IOException {
      int rv = in.read();
      if (rv == -1)
         throw new EOFException();
      return (byte) rv;
   }
   
   /**
    * Read in the next message.  Will block until available.  Stores
    * message internally allowing various match() calls to be made
    * until one succeeds.  Throws EOFException in case of EOF.
    */
   public void read() throws IOException {
      int len = 0;
      while (true) {
         int ch = in_read();
         len = (len << 7) | (ch & 127);
         if (0 != (ch & 128))
            continue;
         break;
      }
      msg = new byte[len];
      for (int a = 0; a<len; a++)
         msg[a] = in_read();
      msg_off = 0;

//      if (ScramJet.DEBUG) {
//         StringBuilder buf = new StringBuilder();
//         for (int a = 0; a<len; a++) {
//            int ch = msg[a];
//            if (ch >= 32 && ch <= 126) {
//               buf.append((char) ch);
//               if (ch == '\\')
//                  buf.append((char) ch);
//            } else {
//               buf.append(String.format("\\x%02X", ch & 255));
//            }
//         }
//         log("Read message: " + buf);
//      }
   }
   
   /**
    * Try to match the loaded message against the format, and if
    * successful, return an array of objects, otherwise null.  %i
    * reads an int, giving an Integer.  %s fetches a string, giving a
    * String.  %r fetches raw data, giving a byte[].  %t fetches
    * remainder of message as raw data, giving a byte[].
    */
   public Object[] match(String fmt) {
      List<Object> rv = new ArrayList<Object>();
      msg_off = 0;
      int len = fmt.length();
      int a = 0;
      try {
         while (a<len) {
            char ch = fmt.charAt(a++);
            if (ch != '%') {
               if (ch != get())
                  return null;
            } else {
               ch = fmt.charAt(a++);
               switch (ch) {
               case 'i':
                  rv.add(new Integer(get_int())); break;
               case 's':
                  rv.add(get_str()); break;
               case 'r':
                  rv.add(get_data()); break;
               case 't':
                  rv.add(get_tail()); break;
               default:
                  return null;
               }
            }
         }
      } catch (EOFException e) {
         return null;
      }
      if (msg_off != msg.length)
         return null;
      return rv.toArray(new Object[0]);
   }

   private int get() throws EOFException {
      if (msg_off >= msg.length)
         throw new EOFException();
      return 255 & msg[msg_off++];
   }

   private int get_int() throws EOFException {
      int val = 0;
      while (true) {
         int ch = get();
         val = (val << 7) | (ch & 127);
         if (0 != (ch & 128))
            continue;
         break;
      }
      return val;
   }

   private byte[] get_data() throws EOFException {
      int len = get_int();
      byte[] rv = new byte[len];
      for (int a = 0; a<len; a++)
         rv[a] = (byte) get();
      return rv;
   }

   private String get_str() throws EOFException {
      return new String(get_data(), Scramjet.charset);
   }

   private byte[] get_tail() {
      int len = msg.length - msg_off;
      byte[] rv = new byte[len];
      for (int a = 0; a<len; a++)
         rv[a] = msg[msg_off+a];
      msg_off = msg.length;
      return rv;
   }

//   private String get_tail_str() {
//      return new String(get_tail(), ScramJet.charset);
//   }
}
      
