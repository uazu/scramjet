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

package net.uazu.scramjet.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.Tool;

/**
 * List running threads
 */
public class SJThreads extends Tool {
   public SJThreads(SJContext sjc) {
      super(sjc);
   }
   public void usage() {
      error("Usage: sj-threads [options]\n" +
            "  -l  Long listing (with stacktraces)");
   }
   public void run() {
      boolean long_opt = false;
      if (args.length == 0)
         long_opt = false;
      else if (args.length == 1 && args[0].equals("-l"))
         long_opt = true;
      else 
         usage();
      
      Map<Thread,StackTraceElement[]> map = Thread.getAllStackTraces();
      List<Thread> keys = new ArrayList<Thread>(map.keySet());
      Collections.sort(keys, new Comparator<Thread>() {
            public int compare(Thread aa, Thread bb) {
               return aa.getName().compareTo(bb.getName());
            }
         });

      for (Thread thr : keys) {
         println(thr.getName() + ": " + thr.getState());
         if (long_opt) {
            for (StackTraceElement st : map.get(thr))
               println("  " + st);
         }
      }
   }
}
