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

package net.uazu.scramjet.mod;

import net.uazu.scramjet.MsgReader;
import net.uazu.scramjet.MsgWriter;
import net.uazu.scramjet.SJModule;
import net.uazu.scramjet.SJProxy;

/**
 * Module which handles calls required for managing the console for
 * ncurses-style applications.
 */
public class ConsoleMod implements SJModule {
   private static final byte[] prefix;
   static {
      prefix = new byte[4];
      prefix[0] = 'c';
      prefix[1] = 'o';
      prefix[2] = 'n';
      prefix[3] = '-';
   }
   
   /**
    * Returns prefix for incoming messages.
    */
   public byte[] getPrefix() {
      return prefix;
   }

   /**
    * MsgWriter
    */
   private MsgWriter writer;
   
   /**
    * MsgWriter
    */
   private SJProxy proxy;
   
   /**
    * Called by SJProxy to setup module.
    */
   public void setup(SJProxy proxy, MsgWriter writer) {
      synchronized (this) {
         this.proxy = proxy;
         this.writer = writer;
         writer.write("con-req-size");
         writer.flush();
         notifyAll();
      }
   }

   /**
    * Wait for startup, used to avoid problems with another thread
    * calling before we are ready.
    */
   private void startup_wait() {
      synchronized (this) {
         while (writer == null) {
            try {
               wait();
            } catch (InterruptedException e) {}
         }
      }
   }

   /**
    * Called by SJProxy just before cleanup.  Flushes the cleanup
    * string and shuts down raw mode.  It isn't strictly necessary to
    * call this, as the cleanup will be done anyway with an atexit
    * handler (which is useful in case of unclean shutdown or
    * crashes).  However, it is useful to call this if errors will be
    * dumped before exit.
    */
   public void cleanup() {
      writer.write("con-term");
      writer.flush();
   }
   
   /**
    * Console window width.  0 initially until size report comes in.
    */
   public int width = 0;

   /**
    * Console window height.  0 initially until size report comes in.
    */
   public int height = 0;

   /**
    * Size change-count.  Increments every time the window size
    * changes.  Can be used to detect size changes.
    */
   public int resize_count = 0;

   /**
    * Current raw input mode: false: off, true: on.
    */
   public boolean raw_mode = false;
   
   /**
    * Attempt to match incoming messages and act on them.
    */
   public boolean match(MsgReader reader) {
      Object[] oa;
      if (null != (oa = reader.match("con-size %i %i"))) {
         int sx = (Integer) oa[0];
         int sy = (Integer) oa[1];
         if (sx != width || sy != height) {
            width = sx;
            height = sy;
            resize_count++;
            window_resized();
         }
         return true;
      }
      return false;
   }

   /**
    * Method which may be overridden to handle window resize events.
    */
   public void window_resized() {
      // do nothing
   }

   /**
    * Turn raw input mode on or off.
    */
   public void rawMode(boolean on) {
      if (writer == null)
         startup_wait();
      if (on == raw_mode) return;
      writer.write(on ? "con-raw-on" : "con-raw-off");
   }

   /**
    * Set a cleanup string which is dumped to the console on exit.
    * This is used in a full-screen app to make sure that cursor is
    * visible and colours are sensible, and that cursor is positioned
    * at the bottom of the screen.
    */
   public void setCleanup(byte[] data, int count) {
      if (writer == null)
         startup_wait();
      writer.write("con-cleanup %t", data, count);
   }

   /**
    * Poll for updates from front-end.  This also receives data for
    * stdin and any other modules.  Doing a read on stdin also does
    * this same poll operation.
    */
   public void poll() {
      proxy.poll_incoming(false);
   }
}
