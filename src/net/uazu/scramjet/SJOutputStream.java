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
import java.io.IOException;

/**
 * Output stream which is attached to a MsgWriter and which allows
 * sending data to the STDOUT or STDERR output of the C front-end.
 */
public class SJOutputStream extends ByteArrayOutputStream {
   public final SJProxy proxy;
   public final String fmt;

   public SJOutputStream(SJProxy proxy, String fmt) {
      this.proxy = proxy;
      this.fmt = fmt;
   }

   public void close() {
      flush();
   }

   public void flush() {
      try {
         super.flush();
         if (count != 0) {
            proxy.writer.write(fmt, buf, count);
            proxy.writer.flush();
         }
         reset();
      } catch (IOException e) {
         // I/O problems probably mean that front end has gone away.
         proxy.curr_tool.exit(199);
      }
   }
}
      
