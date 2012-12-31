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
import net.uazu.event.Event;
import net.uazu.event.ResizeEvent;
import net.uazu.event.UpdateEvent;
import net.uazu.scramjet.ConsoleTool;
import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.Scramjet;

/**
 * Test colours (256/8) and unicode handling
 */
public class ConsoleTest0 extends ConsoleTool implements IAreaUpdateListener {
   public ConsoleTest0(SJContext sjc) { super(sjc); }

   private Area full;
   private Area area;

   @Override
   public void pass(Event ev, List<Event> out) {
      if (ev instanceof ResizeEvent) {
         con.reinit();
         full = con.newArea();
         full.listener = this;
         area = new Area(full, (full.clip.by - 24) / 2, (full.clip.bx - 80) / 2, 24, 80);
         draw();
         eloop.reqUpdate();
         return;
      }
      if (ev instanceof UpdateEvent) {
         if (con.update(full))
            eloop.reqResize();
         full.updated = false;
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
      int end = 0;
      int start = 0;
      int yy = start;
      area.set(yy, 0, 070, "Colour mode: ");
      area.set(yy++, 15, 0170, con.is256Color ? "256-colour" : "8-colour");
      area.set(yy, 0, 070, "Charset:     ");
      area.set(yy++, 15, 0170, "" + Scramjet.charset);
      yy++;

      area.set(yy++, 0, 070, "Attributes:");
      String[] names = { "Brown:        ",
                         "White:        ",
                         "Yellow:       ",
                         "Bright White: " };
      int ii = 0;
      for (String col : new String[] { "%6", "%7", "%16", "%17" } ) {
         area.set(yy, 2, 070, names[ii++]);
         area.set(yy, 17, con.allocateHFB(col + "/%0"), "norm");
         area.set(yy, 22, con.allocateHFB(col + "/%0/B"), "bold");
         area.set(yy, 27, con.allocateHFB(col + "/%0/U"), "undl");
         area.set(yy, 32, con.allocateHFB(col + "/%0/UB"), "both");
         yy++;
      }
      yy++;
      area.set(yy++, 0, 060, " (On 8-colour expect last 2 to be in");
      area.set(yy++, 0, 060, "  yellow-on-red as those colours");
      area.set(yy++, 0, 060, "  don't really exist.  Also bold/ul to");
      area.set(yy++, 0, 060, "  be emulated as colour changes.)");
      end = Math.max(end, yy);

      yy = start;
      area.set(yy++, 40, 070, "Base and highlighted colours:");
      area.set(yy++, 42, 007, "  black   ");
      area.set(yy++, 42, 010, "  blue    ");
      area.set(yy++, 42, 020, "  red     ");
      area.set(yy++, 42, 030, "  magenta ");
      area.set(yy++, 42, 040, "  green   ");
      area.set(yy++, 42, 050, "  cyan    ");
      area.set(yy++, 42, 060, "  yellow  ");
      area.set(yy++, 42, 070, "  white   ");
      yy -= 8;
      area.set(yy++, 52, 0107, "  black   ");
      area.set(yy++, 52, 0110, "  blue    ");
      area.set(yy++, 52, 0120, "  red     ");
      area.set(yy++, 52, 0130, "  magenta ");
      area.set(yy++, 52, 0140, "  green   ");
      area.set(yy++, 52, 0150, "  cyan    ");
      area.set(yy++, 52, 0160, "  yellow  ");
      area.set(yy++, 52, 0170, "  white   ");
      yy++;
      area.set(yy++, 40, 060, " (Expect all 16 colours to be visible");
      area.set(yy++, 40, 060, "  in all cases.  If not, maybe 8-colour");
      area.set(yy++, 40, 060, "  was misdetected as 256-colour.)");
      end = Math.max(end, yy);

      yy = end + 9;
      area.set(yy++, 0, 070, "Hue wheel");
      // Try writing the full-colour one in 8-colour mode anyway to
      // test that allocateHFB doesn't misbehave.  Then overwrite with
      // an 8-colour version.
      int x = 0;
      for (int bg : new int[] {
            0xFF0000, 0xFF1900, 0xFF3300, 0xFF4C00, 0xFF6600,
            0xFF7F00, 0xFF9900, 0xFFB200, 0xFFCC00, 0xFFE500,
            0xFFFF00, 0xE5FF00, 0xCCFF00, 0xB2FF00, 0x99FF00,
            0x7FFF00, 0x66FF00, 0x4CFF00, 0x33FF00, 0x19FF00,
            0x00FF00, 0x00FF19, 0x00FF33, 0x00FF4C, 0x00FF66,
            0x00FF7F, 0x00FF99, 0x00FFB2, 0x00FFCC, 0x00FFE5,
            0x00FFFF, 0x00E5FF, 0x00CCFF, 0x00B2FF, 0x0099FF,
            0x007FFF, 0x0066FF, 0x004CFF, 0x0033FF, 0x0019FF,
            0x0000FF, 0x1900FF, 0x3300FF, 0x4C00FF, 0x6600FF,
            0x7F00FF, 0x9900FF, 0xB200FF, 0xCC00FF, 0xE500FF,
            0xFF00FF, 0xFF00E5, 0xFF00CC, 0xFF00B2, 0xFF0099,
            0xFF007F, 0xFF0066, 0xFF004C, 0xFF0033, 0xFF0019,
         }
      ) {
         area.qset(yy, x++, Console.gencc(' ', con.allocateHFB(0, bg, false, false)));
      }
      if (!con.is256Color) {
         area.set(yy, 0, 002, "          ");
         area.set(yy, 10, 006, "          ");
         area.set(yy, 20, 004, "          ");
         area.set(yy, 30, 005, "          ");
         area.set(yy, 40, 001, "          ");
         area.set(yy, 50, 003, "          ");
      }
      
      yy = end + 1;
      x = 0;
      area.set(yy, x, 070, "ASCII");
      for (int a = 0; a<96; a++)
         area.qset(yy + 1 + a/16, x + 1 + (a & 15), Console.gencc(a+32, 070));
      
      x = 20;
      area.set(yy, x, 070, "Latin1");
      for (int a = 0; a<96; a++)
         area.qset(yy + 1 + a/16, x + 1 + (a & 15), Console.gencc(a + 160, 070));

      x = 40;
      area.box(yy, x+1, 7, 16, 0170);
      area.vstrut(yy, x+6, 7);
      area.vstrut(yy, x+11, 7);
      area.hstrut(yy+2, x+1, 16);
      area.hstrut(yy+4, x+1, 16);

      x = 59;
      Object[] data = {
         Console.vt100_block50, "50%blk",
         Console.vt100_degree, "degree",
         Console.vt100_diamond, "diamond",
         Console.vt100_pi, "pi",
         Console.vt100_ge, ">=",
         Console.vt100_le, "<=",
         Console.vt100_ne, "!=",
         Console.vt100_plusminus, "+/-",
         Console.vt100_scan1, "line-1",
         Console.vt100_scan3, "line-3",
         Console.vt100_scan7, "line-7",
         Console.vt100_scan9, "line-9",
         Console.vt100_bullet, "bullet",
         
         Console.misc_arrowD, "arr-dn",
         Console.misc_arrowL, "arr-lf",
         Console.misc_arrowR, "arr-ri",
         Console.misc_arrowU, "arr-up",
         Console.misc_block100, "block",
         Console.misc_lantern, "lantern",
         Console.misc_block25, "25%brd"
      };

      for (int a = 0; a<data.length; a+=2) {
         int ch = (Integer) data[a];
         String name = (String) data[a+1];
         area.set(yy + (a / 2 % 10), x + 2 + 10 * (a / 20),
                  Console.gencc(ch, 070));
         area.set(yy + (a / 2 % 10), x + 4 + 10 * (a / 20),
                  060, name);
      }
   }

   public void handle_key(KeyEvent kev) {
      if (kev.tag == KP.C_L) {
         eloop.reqResize();
         return;
      }
      exit(0);
   }
}

