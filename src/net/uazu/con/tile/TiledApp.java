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
import java.util.List;

import net.uazu.con.Area;
import net.uazu.con.Console;
import net.uazu.con.IAreaUpdateListener;
import net.uazu.con.KeyEvent;
import net.uazu.event.Event;
import net.uazu.event.EventHandler;
import net.uazu.event.IdleEvent;
import net.uazu.event.ResizeEvent;
import net.uazu.event.UpdateEvent;

/**
 * Subclass this and construct it to create a tiled app.  An event
 * handler is installed to direct all resize, update, idle and key
 * events into the TiledApp.  Override as required: {@link #keyover},
 * {@link #key} and {@link #idle}.
 */
public class TiledApp implements EventHandler, IAreaUpdateListener {
   /**
    * Console
    */
   public final Console con;

   /**
    * Pages
    */
   private List<Page> pages = new ArrayList<Page>();

   /**
    * Currently displayed page
    */
   private Page curr = null;

   /**
    * Set when it is necessary to check the pages to see if any of
    * them need their initial resize on the next UpdateEvent or
    * IdleEvent.
    */
   private boolean pending_resize;
   
   /**
    * Construct a TiledApp to run on the given Console.  Installs a
    * handler on the event loop to direct all events into the TiledApp
    * and its pages.  You can create all the pages of the app in the
    * subclass constructor if you like.
    */
   public TiledApp(Console con) {
      this.con = con;
      con.eloop.addHandler(this);
   }

   /**
    * Add a new page to the TiledApp, and make it the current page if
    * it is the first.  It will get resized soon after this call, but
    * not immediately.
    */
   public void add(Page page) {
      pending_resize = true;
      pages.add(page);
      if (curr == null)
         select(page);
   }

   /**
    * Delete a page from the TiledApp.  If it is the current page,
    * then the first of the remaining pages is selected as the
    * current.
    */
   public void del(Page page) {
      pages.remove(page);
      if (curr == page)
         select(pages.isEmpty() ? null : pages.get(0));
   }

   /**
    * Select a new current page and update the display.
    */
   public void select(Page page) {
      // Stop non-current pages from generating alerts about
      // modifications, but make sure we get a notification from the
      // new current page
      if (curr != null)
         curr.area.updated = true;
      if (page != null)
         page.area.updated = false;
      curr = page;
      con.eloop.reqUpdate();
   }

   /**
    * Implementation of EventHandler.
    */
   public void pass(Event ev, List<Event> out) {
      if (ev instanceof ResizeEvent) {
         resize();
         return;
      }
      if (ev instanceof UpdateEvent) {
         if (pending_resize)
            pending_resize();
         update();
         return;
      }
      if (ev instanceof KeyEvent) {
         keyevent((KeyEvent) ev);
         return;
      }
      if (ev instanceof IdleEvent) {
         if (pending_resize)
            pending_resize();
         idle();
         for (Page page : pages)
            page.idle();
         return;
      }
   }

   /**
    * Implementation of IAreaUpdatedListener.  Makes sure that the
    * current page is automatically updated to the screen after any
    * changes.  The {@link Area#updated} flags of the pages are
    * manipulated to make sure that we only get notifications from the
    * current page.
    */
   public void areaUpdated(Area area) {
      con.eloop.reqUpdate();
   }
      
   /**
    * Handle general resize.
    */
   private void resize() {
      con.reinit();
      for (Page page : pages)
         resize(page, curr != page);
   }

   /**
    * Handle resize of those pages which haven't yet been resized.
    */
   private void pending_resize() {
      for (Page page : pages)
         if (page.area == Area.empty)
            resize(page, curr != page);
      pending_resize = false;
   }

   /**
    * Resize an individual page.
    */
   private void resize(Page page, boolean updated) {   
      Area area = con.newArea(page.hfb < 0 ? 070 : page.hfb);
      area.listener = this;
      area.updated = updated;
      page.relayout(area);
   }

   /**
    * Handle update.
    */
   private void update() {
      if (curr == null) {
         con.update(Area.empty);
         return;
      }
      // Might not have the correct size if just added
      if (curr.area == Area.empty)
         resize(curr, true);
      con.update(curr.area);
      curr.area.updated = false;
   }

   /**
    * Handle key event.
    */
   private void keyevent(KeyEvent kev) {
      if (keyover(kev))
         return;
      if (curr != null && curr.keyevent(kev))
         return;
      if (key(kev))
         return;
      con.beep();
   }

   /**
    * Override this to implement some action on the IdleEvent.  This
    * event is delivered whenever a full batch of event processing
    * (key events, update events, etc) has completed, and the event
    * loop is just about to start waiting for a new event to arrive.
    */
   public void idle() {}

   /**
    * Override this to handle a key event before it is passed to the
    * current page.  This allows global overrides to be applied, for
    * example so that all Pages respond to top-level keys used for
    * switching pages.  If not overridden, the default implementation
    * passes the KeyEvent down to the next handler.
    * @param e KeyEvent to process
    * @return true: event consumed, false: pass the event down to the
    * next handler
    */
   public boolean keyover(KeyEvent e) {
      return false;
   }

   /**
    * Override this to handle a key event that the current page hasn't
    * consumed.  This can provide global default handling for a key if
    * not overridden by the page or its tiles.  If not overridden, the
    * default implementation passes on the KeyEvent, which will
    * generate a beep in the top-level handler.
    * @param e KeyEvent to process
    * @return true: event consumed, false: pass the event up to the
    * next handler
    */
   public boolean key(KeyEvent e) {
      return false;
   }
}