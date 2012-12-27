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

import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.Tool;

/**
 * Test long output, to make sure that output waits properly for next
 * tool in pipe to catch up.
 */
public class LongOutput extends Tool {
   public LongOutput(SJContext sjc) {
      super(sjc);
   }
   public void run() {
      for (int a = 0; a<50000; a++)
         printf("%8d%8d%8d%8d%8d%8d%8d%8d%8d\n", a, a, a, a, a, a, a, a, a);
   }
}
