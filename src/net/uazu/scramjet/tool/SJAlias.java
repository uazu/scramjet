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

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.Scramjet;
import net.uazu.scramjet.Tool;

/**
 * List or add to aliases.
 */
public class SJAlias extends Tool {
   public SJAlias(SJContext sjc) {
      super(sjc);
   }
   public void usage() {
      error("Usage: sj-alias\n" +
            "       sj-alias <alias> <classname>");
   }
   public void run() {
      if (args.length == 0) {
         List<String> keys =
            new ArrayList<String>(Scramjet.aliases.keySet());
         Collections.sort(keys);
         for (String key : keys)
            println(key + "=" + Scramjet.aliases.get(key));
         return;
      }
      if (args.length != 2 || args[0].startsWith("-"))
         usage();
      
      Scramjet.aliases.put(args[0], args[1]);
   }
}
