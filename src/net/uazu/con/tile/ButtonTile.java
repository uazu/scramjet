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

import net.uazu.con.KP;
import net.uazu.con.KeyEvent;

/**
 * A tile which displays a button and which accepts KP.Ret to press
 * the button.  Responds to focus by changing colour and showing
 * marks.  You must implement {@link #action} which is called when the
 * button is 'pressed'.
 */
public abstract class ButtonTile extends Tile {
   private final int focus_hfb;
   private final String text;
   
   /**
    * Construct a ButtonTile
    */
   public ButtonTile(Tile parent, int hfb, int focus_hfb, String text) {
      super(parent, hfb);
      this.focus_hfb = focus_hfb;
      this.text = text;
   }

   /**
    * Implementation of superclass method.
    */
   public void relayout() {
      int width = area.cols;
      StringBuilder buf = new StringBuilder();
      for (int a = 0; a<width; a++)
         buf.append(' ');
      buf.append(text);
      for (int a = 0; a<width; a++)
         buf.append(' ');
      int off = (width + text.length()) / 2;
      String normal_text = buf.substring(off, off + width);
      String focus_text = "[" + buf.substring(off + 1, off + width - 1) + "]";
      if (hasFocus()) {
         area.set(0, 0, focus_hfb, focus_text);
      } else {
         area.set(0, 0, area.hfb, normal_text);
      }
      area.cursor(0, width-1);
   }

   /**
    * Implementation of superclass method.
    */
   public boolean key(KeyEvent e) {
      if (e.tag == KP.Ret) {
         action();
         return true;
      }
      return false;
   }

   /**
    * Implementation of superclass method.
    */
   @Override
   public void focusOn(Tile focus) {
      relayout();
   }
   
   /**
    * Implementation of superclass method.
    */
   @Override
   public void focusOff(Tile focus) {
      relayout();
   }

   /**
    * This method is called when the button is pressed.
    */
   public abstract void action();
}