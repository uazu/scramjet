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

import java.util.Arrays;

/**
 * A buffered area of coloured characters.  A coloured character is
 * formed using Console.gencc(ch, hfb).  See {@link Console} for
 * generating box characters.  All operations respect the current
 * clipping rectangle.  All operations automatically set the {@link
 * #updated} flag and call the update {@link #listener}, except {@link
 * #qset} which requires the caller to call {@link #updated()}
 * themselves.
 *
 * <p>To write coloured and formatted text to the Area, use a
 * temporary {@link CCBuf}.
 */
public class Area {
   // Package-private so that Console.update() can access it
   // efficiently
   final int[] arr;

   /**
    * Default attribute-set index.
    */
   public final int hfb;
   
   /**
    * Backing Area if virtual, or else 'this'.
    */
   private final Area backing;
   
   /**
    * Flashing cursor row, or -1 if cursor is hidden.
    */
   public int curs_y;

   /**
    * Flashing cursor column, or -1 if cursor is hidden.
    */
   public int curs_x;

   /**
    * For non-virtual areas: Virtual area that has focus, or null.  If
    * set, then only the given virtual area will be able to affect the
    * cursor on this area.  Otherwise all areas can affect the cursor
    * position.
    */
   public Area focus;
   
   /**
    * Number of rows in the Area (size-y).
    */
   public final int rows;

   /**
    * Number of columns in the Area (size-x).
    */
   public final int cols;

   /**
    * Rectangle representing the full area: Rect(0, 0, rows, cols).
    */
   public final Rect full;

   /**
    * Base offset into 'arr' if virtual, else 0
    */
   private final int base;

   /**
    * Base row-offset if virtual, else 0.
    */
   public final int base_y;
   
   /**
    * Base column-offset if virtual, else 0.
    */
   public final int base_x;
   
   /**
    * Row-length in 'arr' if virtual, else 'cols'
    */
   private final int pitch;

   /**
    * Clip applied when drawing on this area, which will be {@link
    * #full} clipped according to how much this Area overlaps with the
    * containing areas if it is virtual.  Note that the clip cannot be
    * modified.  To get a smaller (clipped) area to work on, construct
    * a new virtual area using {@link #Area(Area,int,int,int,int)}.
    */
   public final Rect clip;

   /**
    * Object to inform when the {@link #updated} flag is set, or null.
    * (For a virtual Area, updates are handled on the parent and this
    * is ignored.)
    */
   public IAreaUpdateListener listener;

   /**
    * Has this Area been updated since the last time this flag was
    * cleared?  When this flag is first set by calling updated(), the
    * IAreaUpdateListener {@link #listener} is called to request an
    * update at some point in the future.  Following updates do not
    * call the listener.  When the update eventually happens, the
    * update handler clears this flag so that any future changes
    * request another update.  (For a virtual Area, updates are
    * handled on the parent and this is ignored.)
    */
   public boolean updated = false;

   /**
    * Static empty Area, dimensions 0x0, which can be used as a
    * placeholder.
    */
   public final static Area empty = new Area(0, 0);

   /**
    * Construct an area of the given size, initialised to white on
    * black.
    */
   public Area(int sy, int sx) {
      this(sy, sx, 070);
   }

   /**
    * Construct an area of the given size, initialised to the given
    * 0HFB colour.
    */
   public Area(int sy, int sx, int hfb) {
      rows = sy < 0 ? 0 : sy;
      cols = sx < 0 ? 0 : sx;
      this.hfb = hfb;
      full = clip = new Rect(0, 0, rows, cols);
      arr = new int[rows * cols];
      Arrays.fill(arr, 0, arr.length, Console.gencc(' ', hfb));
      base = base_y = base_x = 0;
      pitch = cols;
      backing = this;
      cursorOff();
      updated = false;
   }

   /**
    * Construct a virtual Area backed by the given Area.  A virtual
    * area appears like an independent area but really writes directly
    * to a rectangle within the backing area.  Updates are logged on
    * the backing area and cursor movements are applied there as well.
    * If the specified backing area is virtual, the non-virtual area
    * backing it is the one used as the actual backing area.  It is
    * possible to set up a virtual area that is bigger than the area
    * that it is derived from, in which case only the portion which
    * overlaps the parent area will be affected by drawing operations.
    * The default colour is copied from the given backing area (not
    * the actual one).
    *
    * <p>This can also be used instead of clipping to get a
    * sub-area of the current one to work on.  Forming a virtual area
    * with this constructor does not clear the area.
    * 
    * @param area Backing area
    * @param y Rectangle top-left row
    * @param x Rectangle top-left column
    * @param sy Rectangle height
    * @param sx Rectangle width
    */
   public Area(Area area, int y, int x, int sy, int sx) {
      this(area, y, x, sy, sx, -1);
   }
      
   /**
    * Construct a virtual Area backed by the given Area.  A virtual
    * area appears like an independent area but really writes directly
    * to a rectangle within the backing area.  Updates are logged on
    * the backing area and cursor movements are applied there as well.
    * If the specified backing area is virtual, the non-virtual area
    * backing it is the one used as the actual backing area.  It is
    * possible to set up a virtual area that is bigger than the area
    * that it is derived from, in which case only the portion which
    * overlaps the parent area will be affected by drawing operations.
    *
    * <p>If 'hfb' is 0 or positive, then this is set as the default
    * colour, and the area is cleared.  If 'hfb' is negative, then the
    * default colours are copied from the specified backing area, and
    * it is not cleared.
    * 
    * @param area Backing area
    * @param y Rectangle top-left row
    * @param x Rectangle top-left column
    * @param sy Rectangle height
    * @param sx Rectangle width
    * @param hfb Default colours, or -1 to use colours from 'area'
    */
   public Area(Area area, int y, int x, int sy, int sx, int hfb) {
      boolean clear = hfb >= 0;
      if (!clear) hfb = area.hfb;
      rows = sy < 0 ? 0 : sy;
      cols = sx < 0 ? 0 : sx;
      this.hfb = hfb;
      // Note: clip has to be limited by immediate parent, not by
      // eventual backing area
      full = new Rect(0, 0, rows, cols);
      clip = full.isect(area.clip.move(-y, -x));
      if (area.backing != area) {
         y += area.base_y;
         x += area.base_x;
         area = area.backing;
      }
      base_y = y;
      base_x = x;
      base = y * area.cols + x;
      pitch = area.cols;
      backing = area;
      arr = area.arr;
      cursorOff();
      if (clear) clr();
   }

   /**
    * Check quickly whether we need to update the backing cursor.
    */
   private boolean propagateCursor() {
      return backing != this &&
         (backing.focus == null || backing.focus == this);
   }      
   
   /**
    * Move the flashing cursor to the given position.  The cursor
    * position is used only for controlling the physical flashing
    * cursor that appears on the terminal, not as a position for
    * writing text.  If this is a virtual area, then the backing
    * area's cursor will only be updated if there is no {@link
    * #focus}, or if the focus is on this area.
    */
   public void cursor(int y, int x) {
      // May be called with curs_y, curs_x to propagate cursor to top
      // level
      if (y < 0 || x < 0 || y >= rows || x >= cols) {
         cursorOff();
      } else {
         if (curs_x != x || curs_y != y) {
            curs_y = y;
            curs_x = x;
            if (backing == this)
               updated();
         }
         if (propagateCursor()) {
            // Cursor is also clipped
            if (clip.contains(y, x))
               backing.cursor(y + base_y, x + base_x);
            else
               backing.cursorOff();
         }
      }
   }

   /**
    * Turn off the flashing cursor.
    */
   public void cursorOff() {
      if (hasCursor()) {
         curs_y = curs_x = -1;
         if (backing == this)
            updated();
      }
      if (propagateCursor()) 
         backing.cursorOff();
   }
   
   /**
    * Test whether the flashing cursor is enabled.
    */
   public boolean hasCursor() {
      return curs_x >= 0 && curs_y >= 0;
   }

   /**
    * Set the updated flag if it isn't already set, and notify the
    * listener.  This called automatically for all Area methods,
    * except quick calls like {@link #qset} which require that it be
    * called manually.
    */
   public final void updated() {
      // Everything goes via 'backing' reference
      if (!backing.updated) {
         backing.updated = true;
         if (backing.listener != null)
            backing.listener.areaUpdated(this);
      }
   }

   /**
    * Quickly write a coloured-character to the given location within
    * the area without checking clipping or running {@link
    * #updated()}.  May cause display corruption or throw a runtime
    * exception if point is outside clipping region -- the caller must
    * check clipping themselves.  You must call {@link #updated()} at
    * some point after this call.
    */
   public final void qset(int y, int x, int cch) {
      arr[base + x + y * pitch] = cch;
   }

   /**
    * Write a coloured-character to the given location, clipped by
    * the current clipping rectangle.
    */
   public final void set(int y, int x, int cch) {
      if (y < clip.ay || x < clip.ax || y >= clip.by || x >= clip.bx)
         return;
      arr[base + x + y * pitch] = cch;
      updated();
   }

   /**
    * Write a character to the given location with the given HFB
    * colour, clipped by the current clipping rectangle.
    */
   public final void set(int y, int x, int hfb, int ch) {
      if (y < clip.ay || x < clip.ax || y >= clip.by || x >= clip.bx)
         return;
      arr[base + x + y * pitch] = Console.gencc(ch, hfb);
      updated();
   }

   /**
    * Write a string of characters rightwards from the given location,
    * clipped by the current clipping rectangle.  For anything more
    * complicated, it is best to use {@link CCBuf}.
    */
   public final void set(int y, int x, int hfb, String str) {
      if (y < clip.ay || y >= clip.by || x >= clip.bx)
         return;
      int skip = 0;
      if (x < clip.ax) {
         skip = clip.ax - x;
         x = clip.ax;
      }
      int count = clip.bx - x;

      int offset = base + x + y * pitch;
      int attr = hfb << Console.ATTRSET_SHIFT;
      char hi = 0;
      for (char ch : str.toCharArray()) {
         if (count <= 0)
            break;
         if (Character.isHighSurrogate(ch)) {
            hi = ch;
            continue;
         }
         int cp = ch;
         if (hi != 0 && Character.isSurrogatePair(hi, ch)) {
            cp = Character.toCodePoint(hi, ch);
            hi = 0;
         }
         if (skip > 0) {
            skip--;
            continue;
         }
         arr[offset++] = attr + cp;
         count--;
      }
      updated();
   }

   /**
    * Read a coloured-character from the given location.
    */
   public int get(int y, int x) {
      if (y < 0 || x < 0 || y >= rows || x >= cols)
         return 0;
      return arr[base + x + y * pitch];
   }

   /**
    * Clear the whole area to the default colours (as specified in the
    * constructor).
    */
   public void clr() {
      clr(null, Console.gencc(' ', hfb));
   }   

   /**
    * Clear the whole area to the given coloured-character, clipped
    * by the current clipping rectangle.
    */
   public void clr(int cch) {
      clr(null, cch);
   }

   /**
    * Clear the given range of lines to the given coloured-character,
    * clipped by the current clipping rectangle.
    */
   public void clr(int y, int sy, int cch) {
      clr(y, 0, sy, cols, cch);
   }

   /**
    * Clear the given area to the given coloured-character, clipped
    * by the current clipping rectangle.
    */
   public void clr(int y, int x, int sy, int sx, int cch) {
      clr(new Rect(y, x, y + sy, x + sx), cch);
   }

   /**
    * Clear the given area to the given coloured-character, clipped
    * by the current clipping rectangle.
    */
   public void clr(Rect rect, int cch) {
      rect = rect == null ? clip : clip.isect(rect);
      if (rect.isEmpty()) return;
      int sx = rect.bx - rect.ax;
      int sy = rect.by - rect.ay;

      if (sx == pitch) {
         // Optimise for full-width case
         Arrays.fill(arr, base + rect.ay * pitch, base + rect.by * pitch, cch);
      } else if (sx == 1) {
         // Optimise for single-width case
         int off = base + rect.ax + rect.ay * pitch;
         while (sy-- > 0) {
            arr[off] = cch;
            off += pitch;
         }
      } else {
         // General case
         int off = base + rect.ax + rect.ay * pitch;
         while (sy-- > 0) {
            Arrays.fill(arr, off, off + sx, cch);
            off += pitch;
         }
      }
      updated();
   }

   private void boxadd(int y, int x, int boff) {
      int cch = get(y, x);
      if (cch == 0) return;
      set(y, x, ((cch & Console.ATTRSET_MASK) |
                 Console.box[boff | Console.boxtyp(cch)]));
   }

   /**
    * Draw a cleared box area, with a line around its perimeter,
    * clipped by the current clipping rectangle.
    * @param hfb Attribute-set index
    * @param y Top row
    * @param x Left column
    * @param sy Total height of box
    * @param sx Total width of box
    */
   public void box(int hfb, int y, int x, int sy, int sx) {
      int cc = hfb << Console.ATTRSET_SHIFT;
      clr(y, x, sy, sx, cc+' ');
      clr(y, x+1, 1, sx-2, cc+Console.box[12]);
      clr(y+sy-1, x+1, 1, sx-2, cc+Console.box[12]);
      clr(y+1, x, sy-2, 1, cc+Console.box[3]);
      clr(y+1, x+sx-1, sy-2, 1, cc+Console.box[3]);
      set(y, x, cc+Console.box[10]);
      set(y, x+sx-1, cc+Console.box[6]);
      set(y+sy-1, x, cc+Console.box[9]);
      set(y+sy-1, x+sx-1, cc+Console.box[5]);
      updated();
   }

   /**
    * Draw a vertical strut on an existing box.
    * @param y Top row
    * @param x Column to draw strut in
    * @param sy Total height of strut
    */
   public void vstrut(int y, int x, int sy) {
      boxadd(y++, x, 2);
      while (sy > 2) { sy--; boxadd(y++, x, 3); }
      boxadd(y, x, 1);
   }

   /**
    * Draw a horizontal strut on an existing box.
    * @param y Row to draw strut in
    * @param x Left column
    * @param sx Total width of strut
    */
   public void hstrut(int y, int x, int sx) {
      boxadd(y, x++, 8);
      while (sx > 2) { sx--; boxadd(y, x++, 12); }
      boxadd(y, x, 4);
   }

   /**
    * Copy the given Area to the current area at the given location.
    * This is optimised for the case of copying an area of the same
    * width with ox==0.  The copied area will be clipped if
    * necessary.
    * @return true: something written, false: off clip area
    */
   public boolean put(Area area, int oy, int ox) {
      return put(area, null, oy, ox);
   }

   /**
    * Copy the given Area to the current area at the given location
    * with clipping (in this area's coordinates).  This is optimised
    * for the case of copying an area of the same width with ox==0.
    * The copied area will be clipped if necessary.
    * @return true: something written, false: off clip area
    */
   public boolean put(Area area, Rect clip, int oy, int ox) {
      if (clip == null)
         clip = this.clip;
      else
         clip = this.clip.isect(clip);
      if (clip.isEmpty())
         return false;
      
      int ys0 = 0;
      int ys1 = area.rows;
      int yd0 = oy;
      int yd1 = oy + ys1;
      if (yd0 < clip.ay) { ys0 += clip.ay - yd0; yd0 = clip.ay; }
      if (yd1 >= clip.by) { ys1 += clip.by - yd1; yd1 = clip.by; }
      if (yd0 >= yd1) return false;

      int xs0 = 0;
      int xs1 = area.cols;
      int xd0 = ox;
      int xd1 = ox + xs1;
      if (xd0 < clip.ax) { xs0 += clip.ax - xd0; xd0 = clip.ax; }
      if (xd1 >= clip.bx) { xs1 += clip.bx - xd1; xd1 = clip.bx; }
      if (xd0 >= xd1) return false;

      // Optimise for quick-copy case
      if (xd0 == 0 && xd1 == pitch && pitch == area.pitch) {
         System.arraycopy(area.arr, area.base + ys0 * pitch,
                          arr, base + yd0 * pitch, (yd1-yd0) * pitch);
      } else {
         // Otherwise necessary to handle line by line
         int ys, yd;
         for (ys = ys0, yd = yd0; ys<ys1; ys++, yd++) {
            System.arraycopy(area.arr, area.base + xs0 + ys * area.pitch,
                             arr, base + xd0 + yd * pitch, xd1-xd0);
         }
      }

      updated();
      return true;
   }
}

// END //
