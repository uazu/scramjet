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

package net.uazu.scramjet.test;

import java.io.IOException;

import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.Tool;
import net.uazu.scramjet.mod.ConsoleMod;


/**
 * Test raw input, echo-back and detection of terminal size
 */
public class EchoTest extends Tool {
   public EchoTest(SJContext sjc) {
      super(sjc);
   }
   public void run() {
      ConsoleMod con = new ConsoleMod() {
            public void window_resized() {
               println("New window size: " + width + "x" + height + "\r");
               flush();
            }
         };
      useModule(con);
      con.rawMode(true);
      try {
         while (true) {
            // Blocks
            int ch = stdin.read();
            if (ch == -1 || ch == 3) break;
            print("(" + ch);
            // Flush the rest on the same line
            int avail = stdin.available();
            for (int a = 0; a<avail; a++) {
               ch = stdin.read();
               if (ch == -1 || ch == 3) break;
               print(" " + ch);
            }
            println(")\r");
            flush();
         }
      } catch (IOException e) {}
   }
}
