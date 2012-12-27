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

package net.uazu.scramjet.test.cardgame;

import net.uazu.con.Area;
import net.uazu.con.Console;
import net.uazu.con.Rect;

/**
 * Support for drawing cards and related objects.
 */
public class Draw {
   private static final int[] suit_hfb = { 0172, 0173, 0004, 0006 };
   
   /**
    * Area which is to be written to.
    */
   public final Area area;
   
   /**
    * Card width.
    */
   public static final int WID = 6;
   
   /**
    * Card height.
    */
   public static final int HGT = 5;

   /**
    * Construct a Draw instance for the given area.
    */
   public Draw(Area area) {
      this.area = area;
   }
   
   /**
    * Clear the area to black.
    */
   public void clear() {
      area.clr();
   }
   
   /**
    * Print some text in the given HFB colour.  Does not handle
    * linefeeds or wrapping.
    */
   public void pr(int yy, int xx, int hfb, String str) {
      area.set(yy, xx, hfb, str);
   }
   
   /**
    * Draw a card front at the given position.
    */
   public void card(int yy, int xx, Card cc) {
      int attr = Console.gencc(0, suit_hfb[cc.suit_num]);
      String name = cc.name;
      
      area.clr(yy, xx, 5, 6, attr | ' ');
      area.set(yy, xx+2, attr | name.charAt(0));
      area.set(yy, xx+3, attr | name.charAt(1));
      area.set(yy+4, xx+2, attr | name.charAt(0));
      area.set(yy+4, xx+3, attr | name.charAt(1));
   }
   
   /**
    * Draw a card back at the given position.
    */
   public void back(int yy, int xx) {
      area.clr(yy, xx, 5, 6, Console.gencc('|', 0027));
   }
   
   /**
    * Draw a space where a card could go at the given position.
    */
   public void space(int yy, int xx) {
      area.clr(yy, xx, 5, 6, Console.gencc(' ', 0071));
   }
   
   private final String[] font1 = {
      " 0000    00   00000  00000  00  00 00000   0000  000000  0000   0000        ",
      "00  00  000       00     00 00  00 00     00         00 00  00 00  00   00  ",
      "00  00   00    0000    000  000000 00000  00000     00   0000   00000       ",
      "00  00   00   00         00     00     00 00  00   00   00  00     00   00  ",
      " 0000   0000  000000 00000      00 00000   0000   00     0000   0000        "
   };
   
   private final String[] font2 = {
      "000000   00   000000 000000 00  00 000000 000000 000000 000000 000000       ",
      "00  00   00       00     00 00  00 00     00         00 00  00 00  00   00  ",
      "00  00   00   000000   0000 000000 000000 000000     00 000000 000000       ",
      "00  00   00   00         00     00     00 00  00     00 00  00     00   00  ",
      "000000   00   000000 000000     00 000000 000000     00 000000 000000       "
   };
   
   public final static int BIGDIG_CWID = 7;
   public final static int BIGDIG_CHGT = 5;
   
   /**
    * Draw some text in big characters.  Only digits and colon are
    * available.  Everything else is treated as a space.  Max
    * character sizes are BIGDIG_CWID by BIGDIG_CHGT.  'cc' is a
    * coloured character to draw with.  If 'left' is true, then
    * characters are drawn to the left of (yy,xx) instead of right.
    * If 'square' is true, then a square font is used.  If 'fill' is
    * true then the gaps in the characters are filled with spaces of
    * the same colour, otherwise they are left transparent.
    */
   public void big_digits(
      int yy, int xx,
      int cch, String text,
      boolean left,
      boolean square,
      boolean fill
   ) {
      int cch_fill = Console.swchar(' ', cch);
      char[] data = text.toCharArray();
      
      // Adjust to left of (yy,xx) given.
      if (left) {
         for (char ch : data) {
            xx -= ch == ':' ? BIGDIG_CWID - 2 : BIGDIG_CWID;
         }
      }
      
      String[] font = square ? font2 : font1;
      
      for (char ch : data) {
         int index = 0;
         int wid = BIGDIG_CWID;
         int xbase = xx;
         if (ch >= '0' && ch <= '9') {
            index = (ch - '0') * BIGDIG_CWID;
            xx += wid;
         } else if (ch == ':') {
            index = 10 * BIGDIG_CWID + 1;
            wid = BIGDIG_CWID - 2;
            xx += wid;
         } else {
            xx += BIGDIG_CWID;
            continue;
         }
         if (area.clip.contains(new Rect(yy, xbase, yy+BIGDIG_CHGT, xx))) {
            for (int b = 0; b<BIGDIG_CHGT; b++) {
               String mask = font[b];
               int y = yy + b;
               int x = xbase;
               for (int c = 0; c<wid; c++) {
                  if (mask.charAt(index + c) == '0')
                     area.qset(y, x, cch);
                  else if (fill)
                     area.qset(y, x, cch_fill);
                  x++;
               }
            }
         }
      }
      area.updated();
   }
}
