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
 * Error (not Exception) intended to abort the current thread.
 * Intention is that the tool will not catch this.  If it does,
 * it must re-throw it.  Caught in SJProxy which does cleanup.
 */
public class SJTerminateError extends Error {
   public static final long serialVersionUID = 1343128464893278543L;
   public SJTerminateError() {}
}
      
