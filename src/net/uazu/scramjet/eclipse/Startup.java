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

package net.uazu.scramjet.eclipse;

import net.uazu.scramjet.Scramjet;

import org.eclipse.ui.IStartup;


/**
 * Start scramjet server, i.e. start a number of threads listening on
 * named pipes in $HOME/.scramjet folder.  Has to be done as
 * earlyStartup() because on-demand doesn't work when the demand comes
 * over a named pipe we're not listening on.
 */
public class Startup implements IStartup {
   @Override
   public void earlyStartup() {
      Scramjet.setup();
   }
}
