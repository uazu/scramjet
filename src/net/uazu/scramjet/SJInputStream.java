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

import java.io.InputStream;
import java.util.LinkedList;

/**
 * Input stream which is attached to a SJProxy and which allows access
 * to the STDIN input from the C front-end.  The poll code adds data
 * and flags EOF using the poll_* calls.
 */
public class SJInputStream extends InputStream {
   public final SJProxy proxy;
   private LinkedList<byte[]> list = new LinkedList<byte[]>();
   private boolean eof;
   private int off;   // Read offset
   
   public SJInputStream(SJProxy proxy) {
      this.proxy = proxy;
   }

   /**
    * Called by poll_incoming(): add more data.
    */
   public void poll_add_data(byte[] data) {
      list.add(data);
   }

   /**
    * Called by poll_incoming(): set the EOF flag.
    */
   public void poll_set_eof() {
      eof = true;
   }

   public int available() {
      if (!eof)
         proxy.poll_incoming(false);  // Non-blocking
      
      int rv = 0;
      for (byte[] data : list)
         rv += data.length;
      return rv - off;
   }

   public void close() {
      // Nothing
   }

   public int read() {
      while (list.isEmpty() && !eof) {
         // Block until we have data or reach EOF
         proxy.poll_incoming(true);
      }

      if (list.isEmpty())
         return -1;

      byte[] data = list.getFirst();
      int rv = 255 & data[off++];
      if (off == data.length) {
         list.remove();
         off = 0;
      }
      return rv;
   }

   public int read(byte[] b) {
      return read(b, 0, b.length);
   }
   
   public int read(byte[] out, int out_off, int out_len) {
      while (list.isEmpty() && !eof) {
         // Block until we have data or reach EOF
         proxy.poll_incoming(true);
      }

      if (list.isEmpty())
         return -1;

      int o0 = out_off;
      int o1 = out_off + out_len;
      while (o0 < o1 && !list.isEmpty()) {
         byte[] data = list.getFirst();
         while (off < data.length && o0 < o1)
            out[o0++] = data[off++];
         if (off == data.length) {
            list.remove();
            off = 0;
         }
      }
         
      return o0 - out_off;
   }
}
      
