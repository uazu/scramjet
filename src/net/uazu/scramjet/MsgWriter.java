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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * MsgWriter, which acts as a buffer for encoding and sending messages.
 */
public class MsgWriter {
   private SJProxy proxy;
   private ByteArrayOutputStream otmp;
   private OutputStream out;
   
   /**
    * Construct a MsgWriter instance.
    */
   public MsgWriter(SJProxy proxy, OutputStream out) {
      this.proxy = proxy;
      this.out = out;
      otmp = new ByteArrayOutputStream();
   }
   
   private void
   put(int val) {
      otmp.write(val);
   }

   private void
   put_int(int val) {
      for (int a = 28; a>0; a-=7) {
         if ((val >> a) != 0) {
            for (; a>0; a-=7)
               put(128 | (127 & (val>>a)));
            put(127 & val);
            return;
         }
      }
      put(127 & val);
   }

   private void 
   write_int(int val) throws IOException {
      for (int a = 28; a>0; a-=7) {
         if ((val >> a) != 0) {
            for (; a>0; a-=7)
               out.write(128 | (127 & (val>>a)));
            out.write(127 & val);
            return;
         }
      }
      out.write(127 & val);
   }

//   private void
//   put_tail(byte[] data) {
//      int len = data.length;
//      for (int a = 0; a<len; a++)
//         put(data[a]);
//   }

   private void
   put_data(byte[] data) {
      int len = data.length;
      put_int(len);
      for (int a = 0; a<len; a++)
         put(data[a]);
   }

   private void
   put_str(String str) {
      put_data(str.getBytes(Scramjet.charset));
   }

   /**
    * Write and send a message to the C front-end.  Format string
    * contains %i for encoded-integer (Integer), %s for encoded-string
    * (String), %r for encoded raw data (byte[]), and %t for data at
    * the end of the message (byte[]).  Note that output stream is not
    * flushed.  Use flush() for that.  In case of I/O error terminates
    * the application.  This is most likely caused by the front-end
    * going away.
    */
   public synchronized void
   write(String fmt, Object... args) throws SJTerminateError {
      try {
         otmp.reset();
         
         byte[] tail = null;
         int tail_count = 0;
         int len = fmt.length();
         int acnt = 0;
         for (int a = 0; a<len;) {
            char ch = fmt.charAt(a++);
            if (ch != '%') {
               put(ch);
               continue;
            }
            ch = fmt.charAt(a++);
            switch (ch) {
            case 'i':
               put_int((Integer) args[acnt++]); break;
            case 's':
               put_str((String) args[acnt++]); break;
            case 'r':
               put_data((byte[]) args[acnt++]); break;
            case 't':
               tail = (byte[]) args[acnt++];
               tail_count = (Integer) args[acnt++];
               break;
            default:
               put(ch); break;
            }
         }
         int msglen = otmp.size();
         if (tail != null)
            msglen += tail_count;
         write_int(msglen);
         otmp.writeTo(out);
         if (tail != null)
            out.write(tail, 0, tail_count);
      } catch (IOException e) {
         proxy.do_exit(this, 199);
      }
   }

   /**
    * Flush output stream.
    */
   public void
   flush() throws SJTerminateError {
      try {
         out.flush();
      } catch (IOException e) {
         proxy.do_exit(this, 199);
      }
   }
}
      
