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


/**
 * This class sets up a top-level {@link Page}, a {@link HelpFrame}
 * and a {@link Tile} beneath it.  The Page is available as
 * {@link #page}, the HelpFrame as {@link #help} and this class itself is 
 * the Tile.  By subclassing this class, it is possible to override
 * {@link #relayout}, {@link #keyover} and {@link #key} at the level
 * of the Tile.  Since the Page instance is internal, it is not
 * possible to override methods at that level.  The only override of
 * likely interest is forwarded to this class: {@link #idle}.
 */
public abstract class PageWithHelp extends Tile {
   /**
    * HelpFrame.
    */
   public final HelpFrame help;

   private static class PageHelpFrame extends Page {
      public final HelpFrame help;
      public PageWithHelp tile;
      public PageHelpFrame(TiledApp tapp, int body_hfb, int help_hfb) {
         super(tapp, body_hfb);
         help = new HelpFrame(this, help_hfb);
      }
      @Override
      public void idle() {
         tile.idle();
      }
      @Override
      public void relayout() {
         help.relayout(area, 0, 0, area.rows, area.cols);
      }
   }
   
   /**
    * Construct the PageWithHelp.
    */
   public PageWithHelp(TiledApp tapp, int help_hfb, int body_hfb) {
      super(new PageHelpFrame(tapp, body_hfb, help_hfb).help, body_hfb);
      PageHelpFrame phf = (PageHelpFrame) page;
      help = phf.help;
      phf.tile = this;
      help.setChild(this);
   }

   /**
    * Override this to implement some action on the IdleEvent.  This
    * event is delivered whenever a full batch of event processing
    * (key events, update events, etc) has completed, and the event
    * loop is just about to start waiting for a new event to arrive.
    */
   public void idle() {}
}