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

import net.uazu.con.Console;
import net.uazu.con.EditLine;
import net.uazu.con.KeyEvent;

/**
 * Tile that contains a paragraph/line editor ({@link
 * net.uazu.con.EditLine}).  Takes care of feeding keys to EditLine
 * and redrawing the display, assuming 'focus' is set correctly in the
 * parent.
 */
public class EditLineTile extends Tile {
   /**
    * Embedded editor
    */
   public final EditLine editor;

   /**
    * Constructor
    */
   public EditLineTile(Tile parent, int hfb) {
      super(parent, hfb);
      // TODO: Should Tile keep Console reference?
      while (!(parent instanceof Page))
         parent = parent.parent;
      Console con = ((Page) parent).tapp.con;
      editor = new EditLine(con, hfb);
   }
   
   /**
    * Implementation of superclass method.  Updates editor with new
    * area and redraws.
    */
   @Override   
   public void relayout() {
      editor.setArea(area);
      editor.draw();
   }
   
   /**
    * Implementation of superclass method.  Passes key to editor.
    * Redraws if necessary.  {@link #recolor()} is called if something
    * may have changed that needs recolouring.
    */
   @Override
   public boolean key(KeyEvent e) {
      boolean rv = editor.key(e);

      if (editor.updated) {
         recolor();
         editor.draw();
      }
      return rv;
   }

   /**
    * This class may be overridden in subclasses if they need to add
    * context colouring to the edited text.  It is called whenever the
    * text or cursor position changes.  The default implementation
    * does nothing, leaving the text in the default colours.
    */
   public void recolor() {}
}