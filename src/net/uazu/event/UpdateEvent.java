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

package net.uazu.event;

/**
 * UpdateEvent -- terminal update is required.  This may be requested
 * by code by incrementing {@link EventLoop#update_count}, but it is
 * only normally generated and processed when there is a gap in the
 * processing, when the event queues have completely cleared.
 */
public class UpdateEvent extends Event {
   // No data
}

// END //
