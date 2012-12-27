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
 * This can be used as the top-level Tile to add a help-area at the
 * bottom of the page which shows/hides using F1.  When shown, the
 * help also overrides F2/F3 to allow scrolling up/down.
 */
public class HelpFrame extends Tile {
   private boolean show_help = false;
   private boolean first_time = true;
   private int help_height;
   private int help_yoff = 0;
   private boolean help_has_more = false;
   private Tile child;
   private Area help_area;
   private CCBuf help_text = new CCBuf();
   private Tile help_focus = null;
   
   /**
    * Construct a new HelpFrame.
    */
   public HelpFrame(Tile parent, int hfb) {
      super(parent, hfb);
   }

   /**
    * Set the child tile which will be automatically relaid out in the
    * main part of the display according to whether the help is shown
    * or hidden.
    */
   public void setChild(Tile child) {
      this.child = child;
   }
   
   /**
    * Implementation of superclass method.
    */
   @Override
   public void relayout() {
      // Take 25% of height, maximum 10 rows
      help_height = show_help ? Math.min(10, area.rows / 4) : 0;
      help_area = new Area(area, area.rows - help_height, 0, help_height, area.cols);
      child.relayout(area, 0, 0, area.rows - (show_help ? help_height : 0), area.cols);
      redraw();
   }

   /**
    * Implementation of superclass method.
    */
   @Override
   public boolean keyover(KeyEvent e) {
      if (show_help) {
         switch (e.tag) {
         case F1:
            if (first_time) {
               first_time = false;
               help_text.clear();
            } else {
               show_help = false;
            }
            relayout();
            page.refocus();
            return true;
         case F2:
            scroll(-(help_height / 2));
            return true;
         case F3:
            scroll(help_height / 2);
            return true;
         }
      } else {
         switch (e.tag) {
         case F1:
            show_help = true;
            relayout();
            page.refocus();
            return true;
         }
      }
      return false;
   }

   /**
    * Force an update of the help text if the given tile is in the
    * parent chain from the focus.  If the helptext is showing it is
    * updated immediately, otherwise it is updated next time it is
    * shown.
    */
   public void update(Tile tile) {
      Tile check = help_focus;
      while (check != null && tile != check)
         check = check.parent;
      if (tile == check) {
         help_text.clear();
         redraw();
      }
   }

   /**
    * Redraw help area if showing.
    */
   private void redraw() {
      if (help_area != null && help_area.rows > 0) {
         if (help_text.isEmpty()) {
            if (first_time) {
               help_text.set(
                  006, "  Help window keys:\n" +
                  "  - Use F1 to open and close this window\n" +
                  "  - Use F2/F3 to page up and down within the help text\n" +
                  "  - Press F1 now to clear this message");
            } else {
               Tile tile = help_focus;
               while (help_text.isEmpty() && tile != null) {
                  tile.getHelp(help_text, hfb, this);
                  tile = tile.parent;
               }
               if (help_text.isEmpty())
                  help_text.set(hfb, "  (no help available)");
            }
            help_yoff = 0;
         }
         help_has_more = help_text.draw(help_area, true, help_yoff);
      }
   }

   /**
    * Scroll up or down, if possible.
    */
   private void scroll(int lines) {
      if (lines > 0 && !help_has_more)
         return;
      if (lines < 0 && help_yoff == 0)
         return;
      help_yoff = Math.max(0, help_yoff + lines);
      redraw();
   }
   
   /**
    * Implementation of superclass method.
    */
   @Override
   public void focusOn(Tile focus) {
      help_text.clear();
      help_focus = focus;
      redraw();
   }
}