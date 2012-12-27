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

import java.util.List;

import net.uazu.con.Area;
import net.uazu.con.Console;
import net.uazu.con.IAreaUpdateListener;
import net.uazu.con.KP;
import net.uazu.con.KeyEvent;
import net.uazu.con.Rect;
import net.uazu.event.Event;
import net.uazu.event.ResizeEvent;
import net.uazu.event.UpdateEvent;
import net.uazu.scramjet.ConsoleTool;
import net.uazu.scramjet.SJContext;

/**
 * Test update handling
 */
public class ConsoleTest2 extends ConsoleTool implements IAreaUpdateListener {
   public ConsoleTest2(SJContext sjc) { super(sjc); }

   public Area area;
   private int spacing = 3;
   private boolean spacing_on = false;

   @Override
   public void pass(Event ev, List<Event> out) {
      if (ev instanceof ResizeEvent) {
         con.reinit();
         area = con.newArea();
         area.listener = this;
         draw();
         eloop.reqUpdate();
         return;
      }
      if (ev instanceof UpdateEvent) {
         if (con.update(area))
            eloop.reqResize();
         area.updated = false;
         return;
      }
      if (ev instanceof KeyEvent) {
         handle_key((KeyEvent) ev);
         return;
      }
   }

   @Override
   public void areaUpdated(Area area) {
      eloop.reqUpdate();
   }

   public void draw() {
      String msg = "This is a test.  ";
      int msglen = msg.length();
      Rect clip = area.clip;
      int mid = (clip.ax + clip.bx) / 2;
      int adj = spacing - (mid + clip.by / 2) % spacing;
      for (int y = clip.ay; y<clip.by; y++) {
         int shift = y * 6 / clip.by;
         for (int x = clip.ax; x<clip.bx; x++) {
            char ch = msg.charAt((1000+x-y)%msglen);
            if (spacing_on && ((x + y + adj) % spacing) == 0)
               ch = '/';
            int col = (((x - mid) >> shift) & 3) | 070;
            area.qset(y, x, Console.gencc(ch, col));
         }
      }
      area.set(clip.by - 2, clip.bx / 2 - 22, 006,
               "  PRESS ANY KEY TO CONTINUE, Ctrl-C TO END  ");
      area.updated();
   }

   public void handle_key(KeyEvent kev) {
      if (kev.tag == KP.C_C)
         exit(0);

      if (spacing_on) {
         spacing_on = false;
      } else {
         spacing_on = true;
         spacing = Math.max(3, (spacing + 1) % 20);
      }
      draw();
   }
}

