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

import net.uazu.con.Area;
import net.uazu.con.CCBuf;
import net.uazu.con.KeyEvent;

/**
 * Implement a subclass of Tile to create a particular type of tile
 * for your application (e.g. editor area, mode line, status line,
 * sidebar).  You must implement {@link #relayout} and may override
 * {@link #keyover} and {@link #key}.
 */
public abstract class Tile {
   /**
    * Parent Tile or Page.
    */
   public final Tile parent;

   /**
    * Page this is part of.
    */
   public final Page page;
   
   /**
    * Default colours to fill area with on relayout, or -1 to use
    * parent tile colours.  This is package private, for access by
    * Page.  Actual colours used are saved in area.hfb.
    */
   final int hfb;
   
   /**
    * Area which this tile currently draws on.  It will be a virtual
    * area mapping onto the top-level Area, unless the Tile is
    * actually a Page.
    */
   public Area area;

   /**
    * Construct a new Tile.  If a tile has children, they should be
    * set up at this point.  The {@link #area} is initially a 0x0
    * size, so the children don't need to be laid out yet.  That can
    * be done in relayout() when the parent gets its size.
    * @param parent Parent Tile
    * @param hfb Colours to clear area to on relayout, or -1 to use
    * colours of parent.  The actual colours used are saved in {@code
    * area.hfb}.
    */
   public Tile(Tile parent, int hfb) {
      this.parent = parent;
      this.hfb = hfb;
      area = Area.empty;

      // Search for Page
      Tile tt = this;
      while (tt.parent != null)
         tt = tt.parent;
      page = (Page) tt;
   }

   /**
    * Called by the parent to resize or relocate the tile within the
    * parent Area.  The {@link #area} will be replaced, and {@link
    * #relayout()} will be called.  The area is cleared according to
    * the HFB colour passed to the constructor.  The default colour of
    * the area is stored in {@code area.hfb}.
    */
   public void relayout(Area parent, int y, int x, int sy, int sx) {
      area = new Area(parent, y, x, sy, sx, hfb);
      relayout();
   }

   /**
    * Check whether this tile has focus.
    */
   public boolean hasFocus() {
      return page.getFocus() == this;
   }
   
   /**
    * Relayout and redraw.  This is called after the parent tile
    * replaces {@link #area} with a new area due to a relayout.  The
    * new area will have been cleared to the tile's default HFB.  If
    * the Tile or Page has children, it should call {@link
    * #relayout(Area,int,int,int,int)} on each of them, forcing them
    * to also relayout and redraw.
    */
   public abstract void relayout();

   /**
    * Opportunity to handle a key event as it first descends from
    * TiledApp to Page to Tiles down to focus Tile.  This allows
    * global overrides to be applied, for example so that all tiles on
    * a Page will respond to F1 for help, or that all Pages respond to
    * TiledApp keys used for switching pages.  If not overridden, the
    * default implementation passes the KeyEvent down to the next
    * handler.
    * @param e KeyEvent to process
    * @return true: event consumed, false: pass the event down to the
    * next handler
    */
   public boolean keyover(KeyEvent e) {
      return false;
   }

   /**
    * Opportunity to handle a key event as it ascends back from the
    * most specific focus Tile all the way back up to the TiledApp.
    * This is for normal key processing in the focus Tile, and for
    * providing global defaults at the Page and TiledApp level.  If
    * not overridden, the default implementation passes the KeyEvent
    * up to the next handler.
    * @param e KeyEvent to process
    * @return true: event consumed, false: pass the event up to the
    * next handler
    */
   public boolean key(KeyEvent e) {
      return false;
   }

   /**
    * Called when focus is put on this tile with {@link
    * Page#setFocus}.  Default implementation does nothing.  The
    * {@link Page#setFocus} method automatically handles showing the
    * cursor from the new focus region.  This may be overridden if the
    * tile also needs to change its appearance.
    */
   public void focusOn(Tile focus) {}
   
   /**
    * Called when focus is removed from this tile using {@link
    * Page#setFocus}.  Default implementation does nothing.  This may
    * be used to change the appearance of the tile according to
    * whether it has focus or not.
    */
   public void focusOff(Tile focus) {}

   /**
    * If a HelpFrame is installed as a super-Tile and its help-text
    * area is showing, then this method will be called on the focus
    * tile and each of its parents until a non-blank help-text is
    * returned in the 'buf'.  If no tile in the focus parent chain
    * writes a help-text, then a fallback message will be inserted.
    * If the help-text is context sensitive, then a tile may call
    * {@code help.update(this)} at some later point which will cause
    * this method to be called again to update the help-text, if it is
    * shown.  Default action if not overridden is to not write a
    * help-text.
    * @param buf Blank CCBuf to write help-text to.
    * @param hfb Configured base help colours
    * @param help HelpFrame to call to request a help-text update
    */
   public void getHelp(CCBuf buf, int hfb, HelpFrame help) {}
}