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

/**
 * A card.  There are only 52 of these instances ever created, and
 * they should be treated as immutable.
 */
public class Card {
   /**
    * Name of card: [A23456789XJQK][HDCS]
    */
   public final String name;
   
   /**
    * Number of card: 1-13
    */
   public final int num;
   
   /**
    * Suit of card: 'H', 'D', 'C', 'S'
    */
   public final char suit;
   
   /**
    * Suit of card: 0: hearts, 1: diamonds, 2: clubs, 3: spades
    */
   public final int suit_num;
   
   /**
    * Is this card red? (otherwise black)
    */
   public final boolean is_red;
   
   /**
    * The next card in increasing sequence from Ace to King, or
    * null if off end.
    */
   public Card inc;
   
   /**
    * The previous card in the sequence from Ace to King, or null
    * if off beginning.
    */
   public Card dec;
   
   /**
    * The card with the same number but the next suit in the order
    * {@code Hearts->Diamonds->Clubs->Spades->Hearts}.
    */
   public Card inc_suit;
   
   /**
    * The card with the same number but the next suit in the order
    * {@code Hearts->Diamonds->Clubs->Spades->Hearts}.
    */
   public Card dec_suit;
   
   /**
    * Construct a card based on its spec.
    */
   private Card(String spec) {
      name = spec;
      num = 1 + "A23456789XJQK".indexOf(spec.charAt(0));
      suit = spec.charAt(1);
      suit_num = "HDCS".indexOf(spec.charAt(1));
      is_red = suit_num < 2;
   }
   
   /**
    * Standard deck of 52 cards in order.  Should be treated as
    * immutable.
    */
   public static final Card[] deck;
   
   static {
      deck = new Card[52];
      
      for (int a = 0; a<52; a++) {
         deck[a] = new Card("" + "A23456789XJQK".charAt(a%13) + "HDCS".charAt(a/13));
      }
      for (int a = 0; a<52; a++) {
         if (a % 13 != 12)
            deck[a].inc = deck[a+1];
         if (a % 13 != 0)
            deck[a].dec = deck[a-1];
         deck[a].inc_suit = deck[(a + 13) % 52];
         deck[a].dec_suit = deck[(a + 39) % 52];
      }
   }
}
