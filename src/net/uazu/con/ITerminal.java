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

import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Interface to terminal functionality.
 */
public interface ITerminal {
   /**
    * Get most up-to-date known width.
    */
   public int getSX();

   /**
    * Get most up-to-date known height.
    */
   public int getSY();

   /**
    * Get the character set the terminal operates in.
    */
   public Charset getCharset();

   /**
    * Test whether the terminal supports 256-colour operation.
    */
   public boolean is256Color();
   
   /**
    * Switch in and out of raw input mode.
    */
   public void rawMode(boolean on);

   /**
    * Get the input stream.
    */
   public InputStream getInput();

   /**
    * Output a chunk of data.
    */
   public void output(byte[] data, int len);

   /**
    * Set the chunk of data that should be written on exit to restore
    * cursor/colours/etc.
    */
   public void setCleanup(byte[] data, int len);
}
