// Copyright (c) 2008-2012 Jim Peters, http://uazu.net
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

package net.uazu.con;

import net.uazu.event.EventLoop;

/**
 * Simple one-line or one-paragraph editor based on CCBuf (extending
 * that class) and supporting Emacs-style editing.  Does not support
 * embedded newlines in the text.  Allows CCBuf contents to be drawn
 * on a single line or wrapped into a multi-line region.  Pass in
 * input keypresses using {@link #key}.  You can draw with standard
 * CCBuf methods, or alternatively to handle horizontal/vertical
 * offsets automatically, you can draw with {@link #draw()} after
 * setting the area with {@link #setArea(Area)},
 * {@link #setArea(Area,int,int,int)} or
 * {@link #setArea(Area,int,int,int,int)}.
 * Note: up/down keys will only be enabled in a multi-line region when
 * the area is set up with a setArea() call and this class handles
 * drawing.  Up/Down/PgU/PgD keys will be passed through if cursor is
 * already at the start/end, allowing movement between fields with
 * these same keys to be handled at a higher level.
 */
public class EditLine extends CCBuf {
   /**
    * Console.
    */
   public final Console con;
   
   /**
    * EventLoop.
    */
   public final EventLoop eloop;

   /**
    * Has the text or cursor position been updated?  This is set on
    * all key operations which change things, and is cleared by the
    * {@link #draw} call.
    */
   public boolean updated = false;
   
   /**
    * Additional word-characters
    */
   private String wordchars = "";
   
   /**
    * Colours for inserted characters.
    */
   private final int hfb;
   
   /**
    * Start of editable characters, just after prompt, or 0 if there
    * is no prompt.
    */
   private final int base;

   /**
    * Always wordwrap.
    */
   private static final boolean wordwrap = true;
   
   /**
    * Still appending to paste buffer?
    */
   private boolean killappend = false;

   /**
    * Mark offset, or -1.  Not so long-lasting as in Emacs.  Gets
    * cleared whenever something is inserted or removed.
    */
   private int mark = -1;

   /**
    * Area to draw on.
    */
   private Area area;

   /**
    * Is this a multi-line edit?
    */
   private boolean multi = false;
   
   /**
    * Horizontal offset to use for drawing.
    */
   private int hoff;
   
   /**
    * Vertical offset to use for drawing.
    */
   private int voff;
   
   /**
    * Construct the editor.  
    */
   public EditLine(Console con, int hfb) {
      this(con, hfb, 0);
   }

   /**
    * Construct the editor, reserving the first 'prompt_len'
    * characters for a prompt, which can't be edited.  The prompt
    * characters should be written in using normal CCBuf methods.
    */
   public EditLine(Console con, int hfb, int prompt_len) {
      this.con = con;
      eloop = con.eloop;
      this.hfb = hfb;
      base = prompt_len;
   }

   /**
    * Using this method it is possible to set up additional characters
    * to count as word characters, apart from letters and digits (as
    * judged by Java's Character.isLetterOrDigit()).  All the
    * characters in the string passed will count as word characters.
    * Surrogate-pairs are ignored.
    */
   public void setWordChars(String wordchars) {
      this.wordchars = wordchars;
   }

   /**
    * Set the area to draw on if {@link #draw()} is used.  May be
    * called whenever the target area or area size changes.
    */
   public void setArea(Area area) {
      if (area != this.area) {
         this.area = area;
         multi = area.rows > 1;
         fix_offset();
      }
   }

   /**
    * Set the one-line area to draw on if {@link #draw()} is used.
    * May be called whenever the target area or area size changes.
    */
   public void setArea(Area area, int y, int x, int sx) {
      setArea(new Area(area, y, x, 1, sx));
   }
   
   /**
    * Set the area to draw on if {@link #draw()} is used.  May be
    * called whenever the target area or area size changes.
    */
   public void setArea(Area area, int y, int x, int sy, int sx) {
      setArea(new Area(area, y, x, sy, sx));
   }
   
   /**
    * Draw the edit buffer on the area set up with setArea().  Handles
    * adjusting the horizontal offset according to the cursor
    * position.
    */
   public void draw() {
      // If cursor hasn't been initialised yet
      if (cursor < 0)
         cursor = base;
       
      fix_offset();
      if (multi)
         draw(area, area.full, voff, wordwrap, area.hfb);
      else 
         draw(area, 0, 0, area.cols, hoff, area.hfb);

      updated = false;
   }

   /**
    * Correct vertical or horizontal offset according to cursor
    * position if necessary.
    */
   private void fix_offset() {
      if (multi)
         fix_voff();
      else
         fix_hoff();
   }
   
   /**
    * Recentre horizontal offset according to cursor position if
    * necessary.
    */
   private void fix_hoff() {
      int wid = area.cols;
      int halfwid = wid/2;
      if (cursor > hoff && cursor < hoff + wid-1)
         return;
      if (cursor < wid-1) {
         hoff = 0;
      } else {
         hoff = cursor - halfwid;
      }
   }
   
   /**
    * Recentre vertical offset according to cursor position if
    * necessary.
    */
   private void fix_voff() {
      int curs_y = offsetToYPos(area.full, voff, cursor, wordwrap);
      if (curs_y < 0 || curs_y >= area.rows) {
         curs_y += voff;
         voff = Math.max(0, curs_y - area.rows / 2);
      }
   }
   
   /**
    * Handle a keypress.  If the text is updated in any way then
    * {@link #updated} is set.  This allows recolouring the text
    * before drawing it should context colouring be required.
    * @param e KeyEvent
    * @return Keypress consumed?
    */
   public boolean key(KeyEvent e) {
      KP tag = e.tag;
      char key = e.key;
      
      // If cursor hasn't been initialised yet
      if (cursor < 0)
         cursor = base;
       
      // These actions don't affect 'killappend' mode
      //
      if (tag == KP.C_at) {
         setMark();
         return true;
      }
      if (tag == KP.C_W) {
         if (mark >= base && mark <= len) {
            int o1 = mark < cursor ? mark : cursor;
            int o2 = mark < cursor ? cursor : mark;
            if (!killappend) eloop.clipClear();
            eloop.clipAppend(cut(o1, o2));
            killappend = true;
            clearMark();
            updated = true;
            return true;
         }
         con.beep();
         return true;
      }
      if (tag == KP.M_d) {
         int point = cursor;
         skipFwdNonWord();
         skipFwdWord();
         if (cursor != point) {
            if (!killappend) eloop.clipClear();
            eloop.clipAppend(cut(point, cursor));
            killappend = true;
            clearMark();
            updated = true;
            return true;
         } 
         con.beep();
         return true;
      }
      if (tag == KP.M_BSp) {
         int point = cursor;
         skipBwdNonWord();
         skipBwdWord();
         if (cursor != point) {
            if (!killappend) eloop.clipClear();
            eloop.clipPrepend(cut(cursor, point));
            killappend = true;
            clearMark();
            updated = true;
            return true;
         } 
         con.beep();
         return true;
      }
      if (tag == KP.C_K) {
         if (!killappend) eloop.clipClear();
         eloop.clipAppend(cut(cursor, len));
         killappend = true;
         clearMark();
         updated = true;
         return true;
      }

      // Actions from here on clear 'killappend' mode
      //
      killappend = false;
      if (tag == KP.Left) {
         if (cursor > base) {
            cursor--;
            updated = true;
            return true;
         }
         con.beep();
         return true;
      }
      if (tag == KP.Right) {
         if (cursor < len) {
            cursor++;
            updated = true;
            return true;
         }
         con.beep();
         return true;
      }
      if (tag == KP.C_E) {
         cursor = len;
         updated = true;
         return true;
      }
      if (tag == KP.C_A) {
         cursor = base;
         updated = true;
         return true;
      }
      if (tag == KP.M_f) {
         skipFwdNonWord();
         skipFwdWord();
         updated = true;
         return true;
      }
      if (tag == KP.M_b) {
         skipBwdNonWord();
         skipBwdWord();
         updated = true;
         return true;
      }
      if (multi && area != null) {
         int move = 0;
         if (tag == KP.Up)
            move = -1;
         if (tag == KP.Down)
            move = 1;
         if (tag == KP.PgU)
            move = -area.rows;
         if (tag == KP.PgD)
            move = area.rows;
         if (move != 0) {
            int curs_y = area.curs_y + move;
            int curs_x = area.curs_x;
            int old_cursor = cursor;
            cursor = coordToOffset(area.full, voff, curs_y, curs_x, wordwrap);
            if (old_cursor == cursor)  // Pass through keypresses if already at start/end
               return false;
            updated = true;
            return true;
         }
      }
      
      // Actions from here on down do ins/del and so clear the mark
      //
      clearMark();
      if (tag == KP.C_Y) {
         insString(cursor, hfb, eloop.clip);
         updated = true;
         return true;
      }
      if (tag == KP.KEY) {
         insChar(cursor, hfb, key);
         updated = true;
         return true;
      }
      if (tag == KP.BSp) {
         if (cursor > base) {
            del(cursor-1);
            updated = true;
            return true;
         }
         con.beep();
         return true;
      }
      if (tag == KP.C_D) {
         if (cursor < len) {
            del(cursor);
            updated = true;
            return true;
         }
         con.beep();
         return true;
      }
      if (tag == KP.C_T) {
         if (cursor > base && cursor < len) {
            int tmp = arr[cursor-1];
            arr[cursor-1] = arr[cursor];
            arr[cursor] = tmp;
            cursor++;
            updated = true;
            return true;
         }
         con.beep();
         return true;
      }
      if (tag == KP.M_c) {
         skipFwdNonWord();
         int cp;
         if (cursor < len && Character.isLetter(cp = getChar(cursor))) {
            replace(cursor, Character.toUpperCase(cp));
            cursor++;
         }
         while (cursor < len && Character.isLetterOrDigit(cp = getChar(cursor))) {
            replace(cursor, Character.toLowerCase(cp));
            cursor++;
         }
         updated = true;
         return true;
      }
      if (tag == KP.M_l) {
         skipFwdNonWord();
         int cp;
         while (cursor < len && Character.isLetterOrDigit(cp = getChar(cursor))) {
            replace(cursor, Character.toLowerCase(cp));
            cursor++;
         }
         updated = true;
         return true;
      }
      if (tag == KP.M_u) {
         skipFwdNonWord();
         int cp;
         while (cursor < len && Character.isLetterOrDigit(cp = getChar(cursor))) {
            replace(cursor, Character.toUpperCase(cp));
            cursor++;
         }
         updated = true;
         return true;
      }
      
      // Unknown key
      return false;
   }

   private void replace(int off, int newch) {
      arr[off] = (arr[off] & Console.ATTRSET_MASK) | newch;
   }
   private void clearMark() {
      mark = -1;
   }
   private void setMark() {
      mark = cursor;
   }
   private boolean isWordChar(int cch) {
      int cp = arr[cursor] & Console.CHAR_MASK;
      if (Character.isLetterOrDigit(cp))
         return true;
      return -1 != wordchars.indexOf(cp);
   }
   private void skipFwdNonWord() {
      while (cursor < len && !isWordChar(arr[cursor]))
         cursor++;
   }
   private void skipFwdWord() {
      while (cursor < len && isWordChar(arr[cursor]))
         cursor++;
   }
   private void skipBwdNonWord() {
      while (cursor > base && !isWordChar(arr[cursor-1]))
         cursor--;
   }
   private void skipBwdWord() {
      while (cursor > base && isWordChar(arr[cursor-1]))
         cursor--;
   }
}
