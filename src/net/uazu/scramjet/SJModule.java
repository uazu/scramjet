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

/**
 * Interface that modules must implement to interact with SJProxy.
 */
public interface SJModule {
   /**
    * Get the prefix bytes that incoming messages have if they apply
    * to this module.  This is called often so should return the same
    * byte array each time.
    */
   public byte[] getPrefix();

   /**
    * Sets up the module with the proxy and writer it is connected
    * with.  The module may send messages to the C front-end to
    * initialise itself.
    */
   public void setup(SJProxy sjp, MsgWriter writer);

   /**
    * Try matching against the message in the reader.  Return true if
    * matched and acted upon, or false if not matched.
    */
   public boolean match(MsgReader reader);

   /**
    * Allows the module to clean up just before shutting down, if it
    * needs to.
    */
   public void cleanup();
}
   