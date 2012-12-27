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
 * Rectangle representing an area (immutable value).
 */
public class Rect {
   /**
    * Top coordinate.
    */
   public final int ay;

   /**
    * Left coordinate.
    */
   public final int ax;

   /**
    * Bottom coordinate plus one.
    */
   public final int by;

   /**
    * Right coordinate plus one.
    */
   public final int bx;

   /**
    * Standard empty rectangle.
    */
   public static final Rect empty = new Rect(0, 0, 0, 0);
   
   /**
    * Construct a rectangle from coordinates: top-left (inclusive)
    * and bottom-right (exclusive).
    */
   public Rect(int ay, int ax, int by, int bx) {
      this.ay = ay;
      this.ax = ax;
      this.by = by;
      this.bx = bx;
   }

   /**
    * Copy a rectangle from another one.
    */
   public Rect(Rect rect) {
      ay = rect.ay;
      ax = rect.ax;
      by = rect.by;
      bx = rect.bx;
   }

   /**
    * Test whether this rectangle is empty (== has zero area).
    */
   public boolean isEmpty() {
      return ay == by || ax == bx;
   }
   
   /**
    * Return the intersection of this rectangle with another, or
    * {@link #empty} if there is no intersection.  Argument may be
    * null.
    */
   public Rect isect(Rect clip) {
      if (clip == null)
         return empty;

      int aax = Math.max(ax, clip.ax);
      int bbx = Math.min(bx, clip.bx);
      if (aax >= bbx)
         return empty;

      int aay = Math.max(ay, clip.ay);
      int bby = Math.min(by, clip.by);
      if (aay >= bby)
         return empty;

      return new Rect(aay, aax, bby, bbx);
   }

   /**
    * Return the smallest rectangle that encloses the union of this
    * rectangle with another.  Argument may be null.
    */
   public Rect union(Rect clip) {
      if (clip == null)
         return this;

      return new Rect(
         Math.min(ay, clip.ay),
         Math.min(ax, clip.ax),
         Math.max(by, clip.by),
         Math.max(bx, clip.bx));
   }

   /**
    * Return a new rectangle offset by the given coords.
    */
   public Rect move(int oy, int ox) {
      return new Rect(ay + oy, ax + ox,
                      by + oy, bx + ox);
   }

   /**
    * Check whether the rectangle contains the given point.
    */
   public final boolean contains(int y, int x) {
      return y >= ay && y < by && x >= ax && x < bx;
   }
   
   /**
    * Check whether the given rectangle is completely contained within
    * this rectange.
    */
   public final boolean contains(Rect rect) {
      return rect.ay >= ay && rect.by <= by && rect.ax >= ax && rect.bx <= bx;
   }
      
   /**
    * Check whether the given rectangle is completely contained within
    * this rectange, after moving it by the given offsets.
    */
   public final boolean contains(Rect rect, int oy, int ox) {
      return rect.ay + oy >= ay && rect.by + oy <= by &&
         rect.ax + ox >= ax && rect.bx + ox <= bx;
   }
   
   /**
    * Check whether rectangle contains the given row number.
    */
   public final boolean containsY(int y) {
      return y >= ay && y < by;
   }
   
   /**
    * Check whether rectangle contains the given column number.
    */
   public final boolean containsX(int x) {
      return x >= ax && x < bx;
   }

   /**
    * Debugging string.
    */
   public String toString() {
      return "(" + ay + "," + ax + ") -> (" + by + "," + bx +
         ") size (" + (by-ay) + "," + (bx-ax) + ")";
   }
}

// END //
