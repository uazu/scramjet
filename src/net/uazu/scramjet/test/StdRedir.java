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
import java.io.BufferedReader;
import java.io.InputStreamReader;

import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.Tool;


/**
 * Test redirection of System.in/out/err streams.
 */
public class StdRedir extends Tool {
   public StdRedir(SJContext sjc) { super(sjc); }
   public void run() {
      System.out.println("Output on System.out");
      System.out.println();
      System.err.println("Output on System.err.  Enter a string:");
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      try {
         String line = in.readLine();
         System.err.println("You entered: '" + line + "'");
         System.err.println("Exiting with status 64 ...");
         System.exit(64);
      } catch (IOException e) {
         System.err.println("I/O exception.  Exiting with status 1");
         System.exit(1);
      }
   }
}
