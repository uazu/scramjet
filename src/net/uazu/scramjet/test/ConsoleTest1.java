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
import net.uazu.con.CCBuf;
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
 * Test curses-like console handling.
 */
public class ConsoleTest1 extends ConsoleTool implements IAreaUpdateListener {
   public ConsoleTest1(SJContext sjc) { super(sjc); }

   public Area area;
   public int xx;
   public int yy; 
   private CCBuf cstr = new CCBuf();

   private static final int WID = 20;

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
         output_key((KeyEvent) ev);
         return;
      }
   }

   public void areaUpdated(Area area) {
      eloop.reqUpdate();
   }

   public void draw() {
      String msg = "This is a test.  ";
      int msglen = msg.length();
      Rect clip = area.clip;
      for (int x = clip.ax; x<clip.bx; x++) {
         for (int y = clip.ay; y<clip.by; y++) {
            char ch = msg.charAt((1000+x-y)%msglen);
            int col = ((x * 8) / (clip.bx-clip.ax)) | ((y&15)<<3);
            area.qset(y, x, Console.gencc(ch, col));
         }
      }
      area.updated();
      xx = 0;
      yy = 0;
   }

   public void output_key(KeyEvent kev) {
      if (kev.tag == KP.C_C)
         exit(0);
      
      int col = 0162;
      KP tag = kev.tag;
      String str = tag.toString();
      if (tag.meta)
         col = 0072;
      else if (tag.func)
         col = 0073;
      else if (tag == KP.KEY) {
         str = "[" + kev.key + "]";
         col = 0151;
      }
      cstr.set(col, str, 0);
      cstr.draw(area, yy, xx, WID, 0, col);

      yy++;
      if (yy >= area.rows) {
         yy = 0;
         xx += WID;
         if (xx + WID > area.cols)
            xx = 0;
         area.clear(yy, xx, area.rows, WID, 0070);
      }
   }
}

