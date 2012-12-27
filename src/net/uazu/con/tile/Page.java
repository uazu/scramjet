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

package net.uazu.con.tile;

import java.util.ArrayList;

import net.uazu.con.Area;
import net.uazu.con.KeyEvent;

/**
 * Subclass this and construct it to create a page within a tiled app.
 * A page fills the whole area of the screen, and if required, may be
 * subdivided into tiles.  You must implement {@link #relayout} and
 * may override {@link #keyover}, {@link #key} or {@link #idle}.
 */
public abstract class Page extends Tile {
   public final TiledApp tapp;

   /**
    * Focus tile, or null.
    */
   private Tile focus;

   /**
    * Focus list.  It is optional to use this.  However, if tiles are
    * added to this list, then the {@link #nextFocus} and {@link
    * #prevFocus} calls are enabled, which allow the focus to be moved
    * up and down the list, wrapping around at the start/end.  You can
    * add items with {@link #addFocusTile} or modify this list
    * directly if necessary.
    */
   public final ArrayList<Tile> focus_list = new ArrayList<Tile>();
   
   /**
    * Construct a new page and add it to the given TiledApp.  It will
    * get resized soon after (but not immediately).  If tiles are
    * required, the subclass constructor should create the {@link
    * Tile} instances.
    */
   public Page(TiledApp tapp, int hfb) {
      super(null, hfb);
      this.tapp = tapp;
      tapp.add(this);
   }
   
   /**
    * Called by {@link TiledApp} when the console size changes.  A new
    * Area is passed which should be used as the new backing area of
    * the whole Page.  Forces a relayout and redraw of all the tiles,
    * recursively, and reapplies the focus.
    */
   public void relayout(Area area) {
      this.area = area;
      relayout();
      refocus();
   }

   /**
    * Called by {@link TiledApp}.  Receive a key event from TiledApp,
    * and process it according to the current focus.
    */
   public boolean keyevent(KeyEvent kev) {
      Tile start = focus == null ? this : focus;
      if (callKeyOver(start, kev))
         return true;
      if (callKey(start, kev))
         return true;
      return false;
   }

   // Go up from 'focus', then run keyover on the way back.
   private boolean callKeyOver(Tile tile, KeyEvent kev) {
      if (tile == null)
         return false;
      if (callKeyOver(tile.parent, kev))
         return true;
      if (tile.keyover(kev))
         return true;
      return false;
   }
   // Go up from 'focus', running key() as we go.
   private boolean callKey(Tile tile, KeyEvent kev) {
      if (tile == null)
         return false;
      if (tile.key(kev))
         return true;
      if (callKey(tile.parent, kev))
         return true;
      return false;
   }

   /**
    * Get the focus tile, or return null if there is none.
    */
   public Tile getFocus() {
      return focus;
   }
   
   /**
    * Set the focus tile to the given Tile, or clear the focus if null
    * is passed.  If focus is set, only the cursor from the focus tile
    * is visible on the page.  Keyboard events are delivered to each
    * tile down the chain from the Page to the focus Tile (with {@code
    * keyover()}) and then back up again (with {@code key()}).  If no
    * focus is set, then any tile may set the cursor (the most
    * recently set position will be shown), and keyboard events are
    * only processed in the handlers of the Page itself.
    */
   public void setFocus(Tile focus) {
      Tile oldfocus = this.focus;
      this.focus = focus;

      // Deliver focus-off notification from focus up chain to Page
      if (oldfocus != null) {
         for (Tile tt = oldfocus; tt != null; tt = tt.parent)
            tt.focusOff(oldfocus);
      }

      // Apply the focus to the Area
      refocus();
      
      // Deliver focus-on notification, from focus up chain to page
      if (focus != null) {
         for (Tile tt = focus; tt != null; tt = tt.parent)
            tt.focusOn(focus);
      }
   }

   /**
    * Reapply focus after relayout.  When a top-level {@link
    * #relayout(Area)} call is made by the TiledApp (to handle
    * resize), it automatically reapplies the focus by calling this
    * method.  If you manually relayout part or all of the display, it
    * is necessary to call this manually.  This is necessary because
    * the cursor focus handling is tied to the specific Area of the
    * focus tile, and areas are recreated on a relayout.
    */
   public void refocus() {
      if (focus == null) {
         area.focus = null;
      } else {
         area.focus = focus.area;
         // Reapply cursor to get it shown on top area
         if (focus.area.hasCursor())
            focus.area.cursor(focus.area.curs_y, focus.area.curs_x);
         else
            focus.area.cursorOff();
      }
   }

   /**
    * If the current focus is found in the {@link #focus_list}, move
    * it to the next item in that list, wrapping around at the end.
    * If there is no focus, move it to the first item.
    */
   public void nextFocus() {
      if (focus == null) {
         if (!focus_list.isEmpty())
            setFocus(focus_list.get(0));
         return;
      }
      moveFocus(1);
   }

   /**
    * If the current focus is found in the {@link #focus_list}, move
    * it to the previous item in that list, wrapping around at the
    * start.  If there is no focus, move it to the last item.
    */
   public void prevFocus() {
      if (focus == null) {
         if (!focus_list.isEmpty())
            setFocus(focus_list.get(focus_list.size() - 1));
         return;
      }
      moveFocus(-1);
   }
   
   private void moveFocus(int off) {
      int len = focus_list.size();
      int ii = focus_list.indexOf(focus);
      if (ii >= 0)
         setFocus(focus_list.get((ii + off + len) % len));
   }

   /**
    * Add a tile to the end of {@link #focus_list}.
    */
   public void addFocusTile(Tile tile) {
      if (focus_list.contains(tile))
         throw new IllegalArgumentException();
      focus_list.add(tile);
   }
   
   /**
    * Override this to implement some action on the IdleEvent.  This
    * event is delivered whenever a full batch of event processing
    * (key events, update events, etc) has completed, and the event
    * loop is just about to start waiting for a new event to arrive.
    */
   public void idle() {}
}