// Copyright (c) 2011-2012 Jim Peters, http://uazu.net
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

package net.uazu.scramjet;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import net.uazu.con.Console;
import net.uazu.con.ITerminal;
import net.uazu.event.Event;
import net.uazu.event.EventHandler;
import net.uazu.event.EventLoop;
import net.uazu.scramjet.mod.ConsoleMod;

/**
 * Extend this class if you want to write a console-based fullscreen
 * curses-like tool.  The main loop is started automatically.  The
 * Console is available via {@link #con}, and EventLoop via {@link
 * #eloop}.  Override {@link #pass} to process events.  Override
 * {@link #setup} if you want to do additional setup before the main
 * loop starts.
 *
 * <p>To create a TiledApp-based tool, construct the {@link
 * net.uazu.con.tile.TiledApp} instance within the {@link #setup}
 * routine.  It is not necessary to override {@link #pass} as the
 * TiledApp handler already handles all events.</p>
 */
public class ConsoleTool extends Tool implements EventHandler {
   public ConsoleTool(SJContext sjc) { super(sjc); }

   /**
    * Console handles updating display
    */
   public Console con;
   
   /**
    * Event loop.
    */
   public EventLoop eloop;
   
   /**
    * ConsoleMod communicates with C-layer
    */
   private ConsoleMod mod;

   /**
    * Interface between Console and ConsoleMod/SJContext.
    */
   private ITerminal tif;

   /**
    * Implementation of Tool.run(): Initialise console system.
    */
   public final void run() {
      mod = new ConsoleMod() {
            public void window_resized() {
               eloop.reqResize();
            }
         };
      tif = new ITerminal() {
            public int getSX() {
               return mod.width;
            }
            public int getSY() {
               return mod.height;
            }
            public boolean is256Color() {
               return "1".equals(env.get("SCRAMJET_IS_256_COLOR"));
            }
            public Charset getCharset() {
               // This is the character set detected at JVM startup
               return Scramjet.charset;
            }
            public void rawMode(boolean on) {
               mod.rawMode(on);
            }
            public InputStream getInput() {
               return stdin;
            }
            public void output(byte[] data, int len) {
               writeOut(data, len);
            }
            public void setCleanup(byte[] data, int len) {
               mod.setCleanup(data, len);
            }
         };
      eloop = new EventLoop();
      con = new Console(tif, eloop);

      // Do custom setup
      setup();

      // Add standard handler
      eloop.addHandler(this);
      
      // Initialise module, which will request a resize as soon as the
      // window size is received from the C front-end.  Handling this
      // will initialise the console system.
      useModule(mod);

      // Start main loop
      eloop.loop();
   }

   /**
    * Override this method if you want to do other setup before the
    * main loop starts.  At the time that this runs, 'con' and 'eloop'
    * have already been setup.  The ConsoleMod has not yet been
    * initialised, so no screen size is yet available -- this will be
    * available on the first resize.  The standard event handler has
    * not yet been added.
    */
   public void setup() {}
   
   /**
    * Override this method to handle events: to process or ignore the
    * event and return.  This handler needs to handle ResizeEvent and
    * KeyEvent and optionally UpdateEvent and IdleEvent.  If
    * additional handlers have been added, it is also valid to pass
    * the event on to later handlers using {@code out.add(ev)}, or
    * generate one or more new events and pass those on instead.
    */
   public void pass(Event ev, List<Event> out) {
      out.add(ev);
   }
}

