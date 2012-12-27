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

package net.uazu.scramjet.test.cardgame;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Hand: Represents a list of cards in order.  Can be sorted,
 * loaded, shuffled, etc in place.
 */
public class Hand extends LinkedList<Card> {
   private static final long serialVersionUID = -1417832176324679017L;

   public Hand() {}
   
   /**
    * Add a whole sorted deck of 52 cards to the end of the list.
    */
   public void addDeck() {
      for (Card cc : Card.deck)
         add(cc);
   }
   
   /**
    * Pick a random card from the hand, removing it from the hand.
    * @param rand Random instance to use as source of random numbers
    */
   public Card pickRandom(Random rand) {
      return remove(rand.nextInt(size()));
   }
   
   /**
    * Pick first card out of the hand, removing it and return
    * it, or return null if hand is empty.
    */
   public Card pickFirst() {
      if (isEmpty())
         return null;
      return removeFirst();
   }
   
   /**
    * Pick last card out of the hand, removing it and returning
    * it, or return null if hand is empty.
    */
   public Card pickLast() {
      if (isEmpty())
         return null;
      return removeLast();
   }
   
   /**
    * Pick the head 'cnt' cards, removing them, and return them
    * as a new Hand, in the same order.
    */
   public Hand pickHead(int cnt) {
      Hand rv = new Hand();
      cnt = Math.min(cnt, size());
      while (cnt-- > 0)
         rv.addLast(removeFirst());
      return rv;
   }
   
   /**
    * Pick the tail 'cnt' cards, removing them, and return them
    * as a new Hand, in the same order.
    */
   public Hand pickTail(int cnt) {
      Hand rv = new Hand();
      cnt = Math.min(cnt, size());
      while (cnt-- > 0)
         rv.addFirst(removeLast());
      return rv;
   }
   
   /**
    * Get the first 'cnt' cards in the hand, without removing
    * them, and return as a new Hand, maintaining the same
    * order.
    */
   public Hand peekHead(int cnt) {
      Hand rv = new Hand();
      if (cnt > 0) {
         for (Card cc : this) {
            rv.addLast(cc);
            if (--cnt == 0)
               break;
         }
      }
      return rv;
   }
   
   /**
    * Get the last 'cnt' cards in the hand, without removing
    * them, and return as a new Hand, maintaining the same
    * order.
    */
   public Hand peekTail(int cnt) {
      Hand rv = new Hand();
      Iterator<Card> it = descendingIterator();
      while (cnt > 0 && it.hasNext()) {
         rv.addFirst(it.next());
         cnt--;
      }
      return rv;
   }
   
   /**
    * Shuffle the cards in the hand.
    */
   public void shuffle(Random rand) {
      List<Card> tmp = new ArrayList<Card>();
      while (!isEmpty()) {
         tmp.add(pickRandom(rand));
      }
      addAll(tmp);
   }
}
