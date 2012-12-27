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

/**
 * Variable-length buffer of coloured-characters, designed to be
 * manipulated in place and drawn directly into an Area.  Console
 * coloured-characters can represent all Unicode code-points plus a
 * few VT100 code-page characters (line-drawing/etc), in with 2048
 * attribute-sets (colours/bold/underline).
 *
 * <p>This code correctly converts to/from valid surrogate-pairs in
 * input/output strings.  (Invalid high-surrogates are ignored,
 * invalid low-surrogates are passed through.)
 */
public class CCBuf {
   /**
    * Array of coloured-characters.
    */
   protected int[] arr;
   
   /**
    * Number of coloured-characters.
    */
   protected int len;

   /**
    * Position of the cursor, or -1.
    */
   protected int cursor;

   /**
    * Construct an empty CCBuf.
    */
   public CCBuf() {
      arr = new int[64];
      clear();
   }

   /**
    * Get string length.
    */
   public final int length() {
      return len;
   }

   /**
    * Test whether string is empty.
    */
   public final boolean isEmpty() {
      return len == 0;
   }

   /**
    * Clear the string, leaving CCBuf empty and without a cursor.
    * Note that internal buffer is not shrunk or discarded, so it is
    * more efficient to clear and reuse a single CCBuf than to
    * allocate a new one each time.
    */
   public final void clear() {
      len = 0;
      cursor = -1;
   }

   /**
    * Return the current cursor offset, or -1 if there is no cursor.
    */
   public final int getCursor() {
      return cursor;
   }
   
   /**
    * Put cursor at the end of the string.
    */
   public final void cursor() {
      cursor = len;
   }

   /**
    * Put cursor at the given offset.
    */
   public final void cursor(int off) {
      cursor = off;
   }

   /**
    * Turn off the cursor.
    */
   public final void cursorOff() {
      cursor = -1;
   }

   /**
    * Append a string to the buffer with the given colours, setting
    * the cursor position.
    * @param hfb Colours as 0HFB in octal
    * @param str String to append
    * @param curs Cursor position in 'str', or -1
    */
   public final void add(int hfb, String str, int curs) {
      append(hfb, str, curs);
   }
   
   /**
    * Append a string to the buffer with the given colours.
    * @param hfb Colours as 0HFB in octal
    * @param str String to append
    */
   public final void add(int hfb, String str) {
      append(hfb, str, -1);
   }
   
   /**
    * Append a string and newline to the buffer with the given
    * colours.
    * @param hfb Colours as 0HFB in octal
    * @param str String to append
    */
   public final void addln(int hfb, String str) {
      append(hfb, str, -1);
      addln();
   }
   
   /**
    * Append a formatted string to the buffer with the given colours
    * (formatted with String.format()).
    * @param hfb Colours as 0HFB in octal
    * @param fmt Format
    * @param args Format arguments
    */
   public final void addf(int hfb, String fmt, Object... args) {
      append(hfb, String.format(fmt, args), -1);
   }
   
   /**
    * Clear the buffer and then append a string with the given
    * colours, setting the cursor position.
    * @param hfb Colours as 0HFB in octal
    * @param str String to append
    * @param curs Cursor position in 'str', or -1
    */
   public final void set(int hfb, String str, int curs) {
      clear();
      append(hfb, str, curs);
   }
   
   /**
    * Clear the buffer and then append a string with the given
    * colours.
    * @param hfb Colours as 0HFB in octal
    * @param str String to append
    */
   public final void set(int hfb, String str) {
      clear();
      append(hfb, str, -1);
   }
   
   /**
    * Clear the buffer and then append a string and newline with the
    * given colours.
    * @param hfb Colours as 0HFB in octal
    * @param str String to append
    */
   public final void setln(int hfb, String str) {
      clear();
      append(hfb, str, -1);
      addln();
   }
   
   /**
    * Clear the buffer and then append a formatted string with the
    * given colours (formatted with String.format()).
    * @param hfb Colours as 0HFB in octal
    * @param fmt Format
    * @param args Format arguments
    */
   public final void setf(int hfb, String fmt, Object... args) {
      clear();
      append(hfb, String.format(fmt, args), -1);
   }

   /**
    * Add a wrapping indentation spec.  The indentation specified here
    * will affect all following lines.  It will be inserted on the
    * display before all except the first line of a block of text
    * resulting from wrapping a single line.  This spec must be added
    * at the start of a line.  The same effect as this routine can be
    * gained by inserting the indent followed by a '\r'.
    */
   public final void addWrapIndent(int hfb, String indent) {
      append(hfb, indent, -1);
      append(hfb, "\r", -1);
   }
   
   /**
    * Get coloured-character at given offset.
    */
   public final int get(int off) {
      if (off < 0 || off >= len)
         return 0;
      return arr[off];
   }

   /**
    * Get the character code at the given offset.
    */
   public final int getChar(int off) {
      return get(off) & Console.CHAR_MASK;
   }

   /**
    * Set coloured-character at given offset.  This also allows
    * setting the character just after the end of the string to expand
    * it.  Setting anything else gives an exception.
    */
   public final void put(int off, int cch) {
      if (off < 0 || off >= len) {
         if (off != len)
            throw new ArrayIndexOutOfBoundsException();
         mksp(1);
         len++;
      }
      arr[off] = cch;
   }

   /**
    * Insert a coloured-character at the given offset.
    */
   public final void ins(int off, int cch) {
      mkgap(off, 1);
      arr[off] = cch;
   }

   /**
    * Delete a coloured-character at the given offset.
    */
   public final void del(int off) {
      if (off < 0 || off >= len)
         throw new ArrayIndexOutOfBoundsException();
      delete(off, 1);
   }

   /**
    * Delete coloured-characters from the starting offset to just
    * before the finishing offset.
    */
   public final void del(int o1, int o2) {
      if (o1 < 0 || o1 > o2 || o2 > len)
         throw new ArrayIndexOutOfBoundsException();
      delete(o1, o2-o1);
   }

   /**
    * Cut coloured-characteres from the starting offset to just before
    * the finishing offset, and return the cut characters as a String.
    * This does the same as {@link #del(int,int)}, except for
    * returning a value.
    */
   public final String cut(int o1, int o2) {
      String rv = getString(o1, o2);
      del(o1, o2);
      return rv;
   }

   /**
    * Insert a character at the given offset with the given colours.
    */
   public final void insChar(int off, int hfb, int ch) {
      if (off < 0 || off > len)
         throw new ArrayIndexOutOfBoundsException();
      
      ins(off, (hfb << Console.ATTRSET_SHIFT) | ch);
   }
   
   /**
    * Insert a String at the given offset with the given colours.
    * Valid surrogate pairs are handled.  Invalid high-surrogates are
    * ignored, invalid low-surrogates are passed through.
    * @return Number of characters inserted.
    */
   public final int insString(int off, int hfb, String str) {
      if (off < 0 || off > len)
         throw new ArrayIndexOutOfBoundsException();

      // This way of counting length matches behaviour of loop below
      // (ignores invalid high surrogate, passes through invalid low
      // surrogate)
      char[] chars = str.toCharArray();
      int cnt = 0;
      for (char ch : chars) {
         if (!Character.isHighSurrogate(ch))
            cnt++;
      }

      mkgap(off, cnt);

      int attr = hfb << Console.ATTRSET_SHIFT;
      char hi = 0;
      for (char ch : chars) {
         if (Character.isHighSurrogate(ch)) {
            hi = ch;
            continue;
         }
         int cp = hi != 0 && Character.isSurrogatePair(hi, ch) ?
            Character.toCodePoint(hi, ch) : ch;
         hi = 0;
         arr[off++] = attr | cp;
      } 
      
      return cnt;
   }

   /**
    * Get the whole string.  Note that this does not contain colour
    * information, and VT100 characters are converted to ASCII.
    */
   public String getString() {
      return getString(0, len);
   }

   /**
    * Get the string from the given offset to the end of the buffer.
    * Note that this does not contain any colour information, and
    * VT100 characters are converted to ASCII.
    */
   public String getString(int off) {
      return getString(off, len);
   }

   /**
    * Get the string from offset 'o1' to just before offset 'o2'.
    * Note that this does not contain colour information, and VT100
    * characters are converted to ASCII.
    */
   public String getString(int o1, int o2) {
      StringBuilder buf = new StringBuilder(o2-o1);
      for (int a = o1; a<o2; a++) {
         int cp = arr[a] & Console.CHAR_MASK;
         if (cp < 0x10000)
            buf.append((char) cp);
         else if (cp >= Console.VT100_BASE)
            buf.append((char) (cp - Console.VT100_BASE));
         else 
            buf.appendCodePoint(cp);
      }
      return buf.toString();
   }
   
   /**
    * Append a newline to the buffer.
    */
   public void addln() {
      // Newline doesn't have to be coloured.
      mksp(1);
      arr[len++] = '\n';
   }

   /**
    * Append implementation.
    */
   private final void append(int hfb, String str, int curs) {
      int slen = str.length();
      int attr = hfb << Console.ATTRSET_SHIFT;

      mksp(slen);
      char hi = 0;
      for (int a = 0; a<slen; a++) {
         if (a == curs)
            cursor = len;
         char ch = str.charAt(a);
         if (Character.isHighSurrogate(ch)) {
            hi = ch;
            continue;
         }
         int cp = hi != 0 && Character.isSurrogatePair(hi, ch) ?
            Character.toCodePoint(hi, ch) : ch;
         hi = 0;
         arr[len++] = attr | cp;
      }
      if (slen == curs)
         cursor = len;
   }

   /**
    * Make space in the array for 'req' additional characters at the
    * end.
    */
   private void mksp(int req) {
      req += len;
      if (req <= arr.length)
         return;
      int siz = 2*arr.length;
      while (siz < req) siz *= 2;
      int[] arr2 = new int[siz];
      System.arraycopy(arr, 0, arr2, 0, len);
      arr = arr2;
   }

   /**
    * Make a gap of 'cnt' ints at offset 'off'.  These ints will be
    * uninitialised.  Updates cursor position.
    */
   private final void mkgap(int off, int cnt) {
      if (off < 0 || off > len)
         throw new ArrayIndexOutOfBoundsException();
      if (cursor >= off)
         cursor += cnt;
      mksp(cnt);
      System.arraycopy(arr, off, arr, off+cnt, len-off);
      len += cnt;
   }

   /**
    * Delete 'cnt' shorts at offset 'off'.  Updates cursor position.
    */
   private final void delete(int off, int cnt) {
      if (cursor >= off) {
         if (cursor >= off + cnt) {
            cursor -= cnt;
         } else {
            cursor = off;
         }
      }
      System.arraycopy(arr, off+cnt, arr, off, len-off-cnt);
      len -= cnt;
   }

   /**
    * Draw the CCBuf to fill the given Area, using either the one-line
    * form of draw(), or the multi-line form, according to the size of
    * the Area.  The Area's default colour is used as the padding
    * colour.  Word-wrapping is used for multi-line text.
    * @param area Area to fill
    * @return Overflowed on right side or at bottom?
    */
   public final boolean draw(Area area) {
      if (area.rows == 1)
         return draw(area, 0, 0, area.cols, 0, area.hfb);
      return draw(area, area.full, 0, true, area.hfb);
   }
      
   /**
    * Draw the CCBuf to fill the given Area, using either the one-line
    * form of draw(), or the multi-line form, according to the size of
    * the Area.  The Area's default colour is used as the padding
    * colour.
    * @param area Area to fill
    * @param wordwrap Use word wrapping if this is a multi-line region?
    * @return Overflowed on right side or at bottom?
    */
   public final boolean draw(Area area, boolean wordwrap) {
      if (area.rows == 1)
         return draw(area, 0, 0, area.cols, 0, area.hfb);
      return draw(area, area.full, 0, wordwrap, area.hfb);
   }
      
   /**
    * Draw the CCBuf to fill the given Area, using either the one-line
    * form of draw(), or the multi-line form, according to the size of
    * the Area.  The Area's default colour is used as the padding
    * colour.
    * @param area Area to fill
    * @param wordwrap Use word wrapping if this is a multi-line region?
    * @param off Horizontal or vertical offset to apply.
    * @return Overflowed on right side or at bottom?
    */
   public final boolean draw(Area area, boolean wordwrap, int off) {
      if (area.rows == 1)
         return draw(area, 0, 0, area.cols, off, area.hfb);
      return draw(area, area.full, off, wordwrap, area.hfb);
   }
      
   /**
    * Draw the CCBuf to a one-line region of an Area starting at the
    * given offset, padding with the base colour, and putting overflow
    * indicators at start/end if required.
    *
    * @param y Region row
    * @param x Region left column
    * @param sx Width of region
    * @param off Offset to put at start of region
    * @param hfb Padding colours (used inverted for overflow markers)
    * @return Overflowed on right?
    */
   public final boolean draw(Area area, int y, int x, int sx, int off, int hfb) {
      final int adj = off - x;   // Convert X-pos to offset
      final boolean overflow = len > off + sx;
      final boolean underflow = off > 0;
      
      Rect clip = area.clip.isect(new Rect(y, x, y+1, x+sx));
      if (clip.isEmpty())
         return overflow;
      int x0 = clip.ax;
      int x2 = clip.bx;

      int attr = hfb << Console.ATTRSET_SHIFT;
      int pad = attr | ' ';
      int inv_attr = Console.invert(attr);

      if (overflow && x2 == x + sx) {
         area.qset(y, --x2, inv_attr | '>');
      }
      if (underflow && x0 == x) {
         area.qset(y, x0++, inv_attr | '<');
      }
      int x1 = len - adj;
      if (x1 > x2) x1 = x2;
      
      for (int xx = x0; xx<x1; xx++) {
         if (xx + adj == cursor)
            area.cursor(y, xx);
         area.qset(y, xx, arr[xx + adj]);
      }
      if (x1 + adj == cursor)
         area.cursor(y, x1);
      for (int xx = x1; xx < x2; xx++)
         area.qset(y, xx, pad);
      area.updated();
      return overflow;
   }

   /**
    * Draw string into a rectangular region as multiple wrapped lines,
    * wrapping by words or characters, and filling remaining space
    * with the padding colour.  If a cursor is set and it is visible,
    * then it is displayed.  Overflow markers are always displayed in
    * the rightmost column, which is kept clear for them.
    *
    * <p>Indent for wrapped lines is specified with the indent string
    * followed by '\r' at the start of a line.  The text before the
    * '\r' is stored and used as the wrapping indent on all following
    * wrapped lines.
    *
    * <p>If the text overflows the rectangle, you can use {@link
    * #calcHeight} to calculate the size actually required.  For
    * cursor movement, {@link #coordToOffset} handles moving the
    * cursor up/down visually in the displayed text.  {@link
    * #offsetToYPos} can be used to centre the display on the cursor.
    * 
    * @param area Area to draw on
    * @param rect Rect to fill within the area
    * @param yoff +ve: Number of lines to hide above rectangle (for
    * scrolling), -ve: number of lines of padding to add above text.
    * @param wordwrap true: wrap words, false: wrap characters
    * @param hfb Padding colours (used inverted for overflow markers)
    * @return Overflows at bottom of rectangle?
    */
   public boolean draw(
      Area area, Rect rect, int yoff, boolean wordwrap, int hfb
   ) {
      int attr = hfb << Console.ATTRSET_SHIFT;
      int pad = attr | ' ';
      int inv_attr = Console.invert(attr);

      area.clr(rect, pad);
      int yy = rect.ay;
      if (yoff < 0) {
         yy -= yoff;
         yoff = 0;
      }
      
      Rect clip = area.clip.isect(rect);
      if (clip.isEmpty()) {
         // No drawing required, just establish return value
         LineScan scan = new LineScan(rect.bx - rect.ax - 1, wordwrap);
         while (yoff-- > 0)
            scan.next();
         while (scan.next() && yy < rect.by)
            yy++;
         return scan.more();
      }

      // Wrapping width is one less than full width to allow space for
      // overflow markers
      LineScan scan = new LineScan(rect.bx - rect.ax - 1, wordwrap);
      boolean over_top = yoff > 0;
      while (yoff-- > 0)
         scan.next();
      
      if (len == 0) {
         // Empty buffer with cursor at end
         if (cursor == len)
            area.cursor(yy, rect.ax);
      } else {
         while (yy < rect.by && scan.next()) {
            if (clip.containsY(yy)) {
               int xx = rect.ax;
               for (int a = 0; a<scan.indlen; a++)
                  if (clip.containsX(xx))
                     area.qset(yy, xx++, arr[scan.indpos + a]);
               for (int a = scan.start; a<scan.end; a++) {
                  if (cursor == a) area.cursor(yy, xx);
                  if (clip.containsX(xx))
                     area.qset(yy, xx++, arr[a]);
               }
               if (cursor == scan.end) area.cursor(yy, xx);
            }
            yy++;
         }
      }
      boolean over_bot = scan.more();

      // Overflow markers
      if (over_top)
         area.set(rect.ay, rect.bx-1, inv_attr | '<');
      if (over_bot)
         area.set(rect.by-1, rect.bx-1, inv_attr | '>');
      
      area.updated();
      return over_bot;
   }

   /**
    * Convert an offset into the buffer into a vertical position.
    * This can be used to see what line the cursor is on before
    * drawing it.
    */
   public int offsetToYPos(Rect rect, int yoff, int offset, boolean wordwrap) {
      // Wrapping width is one less than full width to allow space for
      // overflow markers
      LineScan scan = new LineScan(rect.bx - rect.ax - 1, wordwrap);
      int yy = rect.ay - yoff;
      
      while (scan.next()) {
         if (offset <= scan.end)
            return yy;
         yy++;
      }
      return yy;
   }
   
   /**
    * Find the closet offset in the string at or before the given
    * coordinate, with the coordinates corresponding to those produced
    * by the rectangular draw() call.  This is used to handle moving
    * the cursor up and down.
    * @param rect Rectangle specifying area which is filled
    * @param yoff Number of lines to hide above rectangle
    * @param y Coordinate row (in same coord space as 'rect')
    * @param x Coordinate column
    * @param wordwrap true: wrap words, false: wrap characters
    * @return Offset
    */
   public int coordToOffset(Rect rect, int yoff, int y, int x, boolean wordwrap) {
      // Wrapping width is one less than full width to allow space for
      // overflow markers
      LineScan scan = new LineScan(rect.bx - rect.ax - 1, wordwrap);
      int yy = rect.ay - yoff;

      if (y < yy) return 0;
      while (scan.next()) {
         if (y == yy) {
            if (x < rect.ax + scan.indlen)
               return scan.start;
            if (x >= rect.ax + scan.indlen + (scan.end - scan.start))
               return scan.end;
            return scan.start + (x - rect.ax - scan.indlen);
         }
         yy++;
      }
      return len;
   }

   /**
    * Calculate the height of the rectangle required to contain the
    * string for the given width.
    */
   public int calcHeight(int sx, boolean wordwrap) {
      LineScan scan = new LineScan(sx - 1, wordwrap);
      int yy = 0;
      while (scan.next()) 
         yy++;
      return yy;
   }

   /**
    * Scan through lines one by one, handling word-wrapping and
    * indentation.
    */
   private class LineScan {
      private final boolean wordwrap;
      private final int width;
      
      /**
       * Scanning offset
       */
      private int off = 0;

      /**
       * Currently active wrapping indentation region.
       */
      private int c_indpos, c_indlen;
      
      /**
       * Output: indentation start for this line
       */
      public int indpos;

      /**
       * Output: indentation length for this line
       */
      public int indlen;

      /**
       * Output: line start
       */
      public int start;

      /**
       * Output: line end
       */
      public int end;

      /**
       * Constructor.
       */
      public LineScan(int width, boolean wordwrap) {
         this.wordwrap = wordwrap;
         this.width = width < 1 ? 1 : width;
      }

      /**
       * Are there more lines?
       */
      public boolean more() {
         return off < len;
      }

      /**
       * Grab the next line.  Sets up {@link #start}, {@link #end},
       * {@link #indpos} and {@link #indlen}.  If there are no more
       * lines, returns false, otherwise true.
       */
      public boolean next() {
         if (off == len) return false;
         
         int lim = off + width;
         start = off;
         indpos = 0;
         indlen = 0;
         
         char prev = (char) (off == 0 ? '\n' : (arr[off-1] & 255));
         boolean sol = prev == '\n' || prev == '\r';
         if (!sol) {
            indpos = c_indpos;
            indlen = c_indlen;
            lim -= indlen;
            if (lim < off) lim = off;
         }
         
         // No word-wrap
         if (!wordwrap) {
            while (off < len) {
               if (off == lim) {
                  end = off;
                  return true;
               }
               char ch = (char) (arr[off] & 255);
               if (ch == '\n') {
                  end = off++;
                  return true;
               }
               if (ch == '\r') {
                  c_indpos = start;
                  c_indlen = off-start;
                  off++;
                  return next();
               }
               off++;
            }
            end = off;
            return true;
         }
         
         // Word-wrap: default to char-wrap at width if there were no
         // spaces
         end = lim;
         while (off < len) {
            if (off == lim) {
               off = end;
               return true;
            }
            char ch = (char) (arr[off] & 255);
            boolean isspace = ch == ' ';
            if (isspace)  // Break after spaces
               end = off+1;
            if (ch == '\n') {
               end = off++;
               return true;
            }
            if (ch == '\r') {
               c_indpos = start;
               c_indlen = off-start;
               off++;
               return next();
            }
            off++;
         }
         if (off == len)
            end = len;
         return true;
      }
   }
}
