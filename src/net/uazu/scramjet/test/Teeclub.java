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

package net.uazu.scramjet.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.uazu.con.CCBuf;
import net.uazu.con.Console;
import net.uazu.con.KP;
import net.uazu.con.KeyEvent;
import net.uazu.con.Rect;
import net.uazu.con.tile.Page;
import net.uazu.con.tile.Tile;
import net.uazu.con.tile.TiledApp;
import net.uazu.event.Timer;
import net.uazu.scramjet.ConsoleTool;
import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.test.Teeclub.Scores.Score;
import net.uazu.scramjet.test.cardgame.Card;
import net.uazu.scramjet.test.cardgame.Draw;
import net.uazu.scramjet.test.cardgame.Hand;

/**
 * Teeclub, a patience card game.  This demonstrates TiledApp for a
 * simple app with two pages, and two tiles on the play page.  It also
 * demonstrates two levels of key handling (C_C and C_L at the top
 * level, and per-page handling).  It also demonstrates using a timer.
 */
public class Teeclub extends ConsoleTool {
   public Teeclub(SJContext sjc) { super(sjc); }
   
   /**
    * Handle startup of ConsoleTool, and pass control to TiledApp.
    */
   public void setup() {
      tapp = new TiledApp(con) {
            public boolean key(KeyEvent kev) {
               //log("Key: " + kev);
               return false;
            }
            public boolean keyover(KeyEvent kev) {
               if (kev.tag == KP.C_C) {
                  con.eloop.reqAbort();
                  return true;
               }
               if (kev.tag == KP.C_L) {
                  con.reinit();
                  con.eloop.reqUpdate();
                  return true;
               }
               return false;
            }
         };
      intro = new IntroPage(tapp);
      play = new PlayPage(tapp);
      scores = new Scores(
         new File(env.get("HOME") + File.separator +
                  ".scramjet" + File.separator +
                  "teeclub-scores.txt"));
      if (scores.file.exists()) {
         try {
            scores.load();
         } catch (IOException e) {
            scores = null;
            scores_error = "Error loading scores: " + e.getMessage();
         }
      }

      // Get username for hi-score list
      user = env.get("USER");
      if (user == null)
         user = env.get("LOGNAME");
      if (user == null) {
         user = env.get("HOME");
         int ii = user.lastIndexOf(File.separator);
         if (ii >= 0)
            user = user.substring(ii+1);
      }
   }
   
   private static final int HELP_HFB = 0006;
   private static final int HELP_SY = 10;
   private TiledApp tapp;
   private IntroPage intro;
   private PlayPage play;
   private boolean show_help;
   private Draw draw;
   private Game game;
   private Scores scores;
   private String scores_error;
   private Score last_score;
   private String user;

   /**
    * Intro and high-score page.
    */
   private class IntroPage extends Page {
      private final String[] logo = {
         "  ___________________________________      ",
         " / ____  ____________________________\\     ",
         "/ /   / /                __      __  __    ",
         "\\ \\  / / ___  ___  _____/ /_  __/ /_ \\ \\   ",
         " \\/ / / / _ \\/ _ \\/ ___/ / / / / __ \\ \\ \\  ",
         "   / / /  __/  __/ /__/ / /_/ / /_/ /  \\ \\ ",
         "   \\_\\ \\___/\\___/\\___/_/\\__,_/_.___/ /_ \\ \\",
         "     ___________________________________/ /",
         "     \\___________________________________/ "
      };
      private final int[] logo_hfb = {
         0160, 0140, 0150, 0110, 0130, 0120
      };
      public IntroPage(TiledApp tapp) {
         super(tapp, 070);
      }
      public void relayout() {
         boolean squash = area.rows < 28;
         int yy = squash ? 0 : 1;
         int logo_hgt = logo.length;
         drawLogo(yy, 0, logo_hgt, area.cols);
         yy += logo_hgt + (squash ? 1 : 2);
         int yend = area.rows-4;
         int extra = yend - yy - 12;
         if (extra > 0) 
            yy += extra/2;
         yend = Math.min(yy + 12, yend);
         if (yend - yy >= 3) {
            final int WID = 24;
            int sc_xx = (area.cols - WID)/2;
            area.set(yy, sc_xx, 0171, "  H I G H  S C O R E S  ");
            yy++;
            area.set(yend, sc_xx, 0171, "                        ");
            int vbar = Console.gencc(Console.box[3], 010);
            area.fill(yy, sc_xx, yend-yy, 1, vbar);
            area.fill(yy, sc_xx+WID-1, yend-yy, 1, vbar);
            drawScores(new Rect(yy, sc_xx + 2, yend, sc_xx+WID-2));
         }
         area.set(area.rows-2, (area.cols - 31)/2, 0070, "Press N for new game, Q to quit");
      }
      
      private void drawLogo(int yy, int xx, int sy, int sx) {
         xx += (sx - logo[0].length()) / 2;
         if (!area.clip.contains(new Rect(yy, xx, yy+logo.length, xx+logo[0].length())))
            return;
         for (String txt : logo) {
            int x = xx;
            for (char ch : txt.toCharArray())
               area.qset(yy, x++, Console.gencc(ch, logo_hfb[(100+x+yy)/5%6]));
            yy++;
         }
      }
      private void drawScores(Rect rect) {
         if (scores == null) {
            CCBuf buf = new CCBuf();
            buf.add(0120, scores_error, -1);
            buf.draw(area, rect, 0, true, 0120);
            return;
         }
         area.clear(rect, 070);
         int wid = rect.bx-rect.ax;
         boolean found = false;
         int ii = 1;
         int last = rect.by-rect.ay;
         for (Score sc : scores.scores) {
            if (ii == last)
               break;
            int hfb = 070;
            if (sc == last_score) {
               found = true;
               hfb = 007;
            }
            area.set(rect.ay+ii-1, rect.ax, hfb, sc.format(ii, wid));
            ii++;
         }
         if (last_score != null && !found)
            area.set(rect.ay+ii-1, rect.ax, 007, last_score.format(0, wid));
      }
      public boolean key(KeyEvent kev) {
         switch (kev.tag) {
         case KEY:
            if (kev.key == 'q' || kev.key == 'Q') {
               con.eloop.reqAbort();
               return true;
            }
            if (kev.key == 'n' || kev.key == 'N') {
               new_game();
               return true;
            }
            return false;
         case Esc:
            con.eloop.reqAbort();
            return true;
         }
         return false;
      }
      public void new_game() {
         game = new Game() {
               public void done(int time) {
                  if (time >= 0 && scores != null) {
                     last_score = scores.add(time, user);
                     try {
                        scores.save();
                     } catch (IOException e) {
                        scores = null;
                        scores_error = "Failed to save high-scores: " + e.getMessage();
                     }
                     relayout();
                  }
                  game = null;
                  tapp.select(intro);
               }
            };
         play.relayout();
         tapp.select(play);
      }
   }

   /**
    * Main play page.
    */
   private class PlayPage extends Page {
      private Tile main;
      private HelpTile help;
      public PlayPage(TiledApp tapp) {
         super(tapp, 070);
         main = new Tile(this, 070) {
               public void relayout() {
                  draw = new Draw(area);
                  if (game != null)
                     game.draw();
               }
            };
         help = new HelpTile(this);
      }
      public void relayout() {
         int help_sy = !show_help ? 0 : Math.min(HELP_SY, area.rows/4);
         main.relayout(area, 0, 0, area.rows-help_sy, area.cols);
         help.relayout(area, area.rows-help_sy, 0, help_sy, area.cols);
      }
      public boolean key(KeyEvent kev) {
         switch (kev.tag) {
         case KEY:
            if (kev.key >= '1' && kev.key <= '9') {
               if (game.cards_selected()) {
                  game.move_to(kev.key - '1');
               } else {
                  game.select(kev.key - '1');
               }
               return true;
            }
            switch (kev.key) {
            case '-':
               if (game.cards_selected())
                  game.select_less();
               return true;
            case '+':
               if (game.cards_selected())
                  game.select_more();
               return true;
            case ' ':
               if (game.cards_selected())
                  game.unselect();
               else
                  game.drop();
               return true;
            case 'Q':
               game.finish(false);
               return true;
            case 'R':
               game.finish(false);
               intro.new_game();
               return true;
            }
            return false;
         case F1:
            show_help = !show_help;
            relayout();
            return true;
         case Esc:
            game.finish(false);
            return true;
         case Up:
            if (show_help)
               help.move(-1);
            return true;
         case Down:
            if (show_help)
               help.move(1);
            return true;
         case PgU:
            if (show_help)
               help.move(-(help.area.rows-1));
            return true;
         case PgD:
            if (show_help)
               help.move(help.area.rows-1);
            return true;
         }
         return false;
      }
   }

   public static class HelpTile extends Tile {
      public String text =
         " Keys:\n" +
         "   F1                  Show or hide this helptext\n" +
         "   Up/Down/PgU/PgD     Scroll this helptext\n" +
         "   Shift-Q or Esc      Quit game\n" +
         "   Shift-R             Restart: start a new game\n" +
         "   <digit>             Select cards in corresponding column\n" +
         "   + or -              Increase or decrease number of selected cards\n" +
         "   <same-digit>        Same digit moves selected cards to top\n" +
         "   <other-digit>       Other digit moves selected cards to that column\n" +
         "   Space               Bring a new card down from the deck top-left\n" +
         "\n" +
         " Teeclub rules:\n" +
         "   \r   The aim is to move all the cards up to the top area. " +
         "Each top pile must consist of one suit only, " +
         "stacked in order from Ace up to King. " +
         "The cards in the main area below can be moved around: " +
         "runs of one or more cards of the same suit may be moved on " +
         "top of a card with the next-higher number, of any suit. " +
         "The bulk of the cards are in the deck top-left. "+
         "Cards may be brought down from the deck " +
         "to the leftmost column using Space. " +
         "Moving cards is done with the digit keys, first key to " +
         "select the source column, second key to select the destination column. " +
         "If not all cards can be moved, the maximum number possible are moved. " +
         "To move cards up to the top, " +
         "select the source column by pressing its digit twice. " +
         "To move less than the full number of cards up to the top, " +
         "select the column with one press, adjust with plus and minus keys, " +
         "and then press the column digit again to complete the operation.\n";

      public CCBuf buf = new CCBuf();
      public int yoff = 0;
      public boolean more = false;
      
      public HelpTile(Tile parent) {
         super(parent, HELP_HFB);
         buf.set(HELP_HFB, text);
      }
      public void relayout() {
         more = buf.draw(area, true, yoff);
      }
      public void move(int off) {
         if (off > 0 && !more)
            return;
         yoff += off;
         if (yoff < 0)
            yoff = 0;
         relayout();
      }
   }

   /**
    * Handles all the state of a running game.
    */
   public abstract class Game {
      public boolean active = true;
      public long start;
      public int time;   // In seconds

      public Random rand;
      public Hand stack;
      public Hand[] row;
      public Hand[] home;
      public int sel_row = -1;
      public int sel_cnt = 0;
      public int sel_max = 0;
      
      /**
       * Create a new Game, shuffling the cards, setting up the
       * initial configuration and starting the clock.
       */
      public Game() {
         rand = new Random();
         stack = new Hand();
         stack.addDeck();
         stack.addDeck();
         stack.shuffle(rand);
         stack.shuffle(rand);
         
         row = new Hand[9];
         for (int a = 0; a<9; a++) {
            row[a] = new Hand();
            for (int b = 0; b<5; b++)
               row[a].add(stack.pickLast());
         }
         
         home = new Hand[8];
         for (int a = 0; a<8; a++)
            home[a] = new Hand();
         
         start = System.currentTimeMillis();
         con.eloop.addTimer(new Timer(start + 1000) {
               public long run(long req, long now) {
                  if (!active) return 0;
                  time = (int) ((now - start) / 1000);
                  req = start + (time + 1) * 1000;
                  draw();
                  return req;
               }
            });
      }
      
      /**
       * Are any cards selected?
       */
      public boolean cards_selected() {
         return sel_row >= 0 && sel_cnt > 0;
      }
      
      /**
       * Mark the game as finished: stop timer.
       */
      public void finish(boolean won) {
         active = false;
         done(won ? time : -1);
      }

      /**
       * Called when the game is over.
       * @param time Winning time, or -1 if gave up
       */
      public abstract void done(int time);
      
      /**
       * Return a Hand of cards representing the run of cards at the
       * bottom of the given row.
       */
      private Hand final_run(Hand row) {
         Hand rv = new Hand();
         Iterator<Card> it = row.descendingIterator();
         if (it.hasNext()) {
            Card cc = it.next();
            rv.add(cc);
            while (it.hasNext()) {
               Card cc1 = it.next();
               if (cc1 != cc.inc)
                  break;
               cc = cc1;
               rv.add(cc);
            }
         }
         return rv;
      }
      
      /**
       * Select cards in the given row.
       */
      public void select(int rr) {
         Hand hh = final_run(row[rr]);
         if (!hh.isEmpty()) {
            sel_row = rr;
            sel_cnt = hh.size();
            sel_max = hh.size();
            draw();
         } else {
            con.beep();
         }
      }
      
      /**
       * Increase selection.
       */
      public void select_more() {
         if (sel_cnt < sel_max) {
            sel_cnt++;
            draw();
         }
      }
      
      /**
       * Decrease selection.
       */
      public void select_less() {
         if (sel_cnt > 0) {
            sel_cnt--;
            draw();
         }
      }
      
      /**
       * Undo selection.
       */
      public void unselect() {
         sel_row = -1;
         sel_cnt = 0;
         draw();
      }
      
      private void unselect_and_draw() {
         unselect();
      }
      
      /**
       * Move selected cards to a different row, or up to the top if
       * the same row is specified.
       */
      public void move_to(int rr) {
         if (!cards_selected()) {
            error(); return;
         }
         
         if (rr == sel_row) {
            move_to_top();
            return;
         }
         
         if (row[rr].isEmpty()) {
            row[rr].addAll(row[sel_row].pickTail(sel_cnt));
            unselect_and_draw();
            return;
         }
         
         Card last = row[rr].getLast();
         Card from_last = row[sel_row].getLast();
         if (last == null || from_last == null) {
            error(); return;
         }
         int cnt = last.num - from_last.num;
         if (cnt > sel_cnt) {
            error(); return;
         }
         row[rr].addAll(row[sel_row].pickTail(cnt));
         unselect_and_draw();
      }
      
      /**
       * Move current selected cards to home area.
       */
      public void move_to_top() {
         if (!cards_selected()) {
            error(); return;
         }
         
         Card from_last = row[sel_row].getLast();
         if (from_last.num == 1) {
            for (int a = 0; a<8; a++) {
               if (home[a].isEmpty()) {
                  for (int b = sel_cnt; b>0; b--)
                     home[a].add(row[sel_row].pickLast());
                  check_finished();
                  unselect_and_draw();
                  return;
               }
            }
            error(); return;
         }
         for (int a = 0; a<8; a++) {
            if (!home[a].isEmpty() &&
                home[a].getLast().inc == from_last) {
               for (int b = sel_cnt; b>0; b--)
                  home[a].add(row[sel_row].pickLast());
               check_finished();
               unselect_and_draw();
               return;
            }
         }
         error(); return;
      }
      
      /**
       * Test whether the game has finished.
       */
      private void check_finished() {
         for (Hand hh : home)
            if (hh.size() != 13)
               return;
         finish(true);
      }
      
      /**
       * Beep and clear selection.
       */
      private void error() {
         con.beep();
         unselect_and_draw();
      }
      
      /**
       * Drop the next card down from the stack to row 0.
       */
      public void drop() {
         if (!stack.isEmpty()) {
            row[0].add(stack.pickLast());
            draw();
         }
      }
      
      private final int OX = 2;
      private final int OY = 1;
      
      /**
       * Draw the cards onto the page.
       */
      public void draw() {
         draw.clear();

         draw.pr(draw.area.rows-1, draw.area.cols-10, HELP_HFB, " F1: Help ");
         
         draw.big_digits(
            draw.area.rows - Draw.BIGDIG_CHGT - 2, 74,
            Console.gencc('/', 0010),
            String.format("%2d:%02d", time / 60, time % 60),
            true, false, true   // left, square, fill
         );
         
         if (stack.isEmpty())
            draw.space(OY, OX);
         else
            draw.back(OY, OX);
         
         for (int a = 0; a<8; a++) {
            if (home[a].isEmpty())
               draw.space(OY, OX + a*8 + 8);
            else
               draw.card(OY, OX + a*8 + 8, home[a].getLast());
         }
         
         for (int a = 0; a<9; a++) {
            int yy = OY + 5;
            int v1 = a+1;
            draw.pr(yy++, OX + a*8, 0070, "" + v1);
            
            int len = row[a].size();
            int bot = yy + len + Draw.HGT + 1;
            int skip = 0;
            if (bot > draw.area.rows+1)
               skip = bot - draw.area.rows;
            
            if (skip > 0)
               draw.pr(yy++, OX + a*8, 0070, "::::::");
            
            int sel = 9999;
            if (sel_row == a && sel_cnt > 0)
               sel = row[a].size() - sel_cnt;

            for (Card cc : row[a]) {
               sel--;
               if (skip-- > 0)
                  continue;
               
               if (sel >= 0)
                  draw.card(yy++, OX + a*8, cc);
               else
                  draw.card(1 + yy++, OX + 1 + a*8, cc);
            }
         }
      }
   }

   /**
    * High-score table.
    */
   public static class Scores {
      public static class Score implements Comparable<Score> {
         /**
          * Time in seconds.
          */
         public final int time;

         /**
          * User.
          */
         public final String user;

         public Score(int time, String user) {
            this.time = time;
            this.user = user;
         }

         /**
          * Sort with lowest time first.
          */
         public int compareTo(Score bb) {
            return time < bb.time ? -1 :
               time > bb.time ? 1 : 0;
         }

         public String format(int ii, int len) {
            String str = ii > 0 ? String.format("%2d  ", ii) : "--  ";
            str += String.format("%2d:%02d  %-100s", time/60, time%60, user);
            if (str.length() > len)
               str = str.substring(0, len);
            return str;
         }
      }

      /**
       * Maximum number of scores to keep.
       */
      private static final int MAX_SCORES = 20;
      
      /**
       * Scores.
       */
      public final List<Score> scores = new ArrayList<Score>();

      /**
       * File for load/save.
       */
      public final File file;
      
      /**
       * Construct to load/save from given File.
       */
      public Scores(File file) {
         this.file = file;
      }
      
      /**
       * Save scores to file.
       * @throws IOException 
       */
      public void save() throws IOException {
         PrintWriter out = new PrintWriter(file);
         out.println("# Teeclub high scores");
         for (Score sc : scores)
            out.println(sc.time + " " + sc.user);
         out.close();
         if (out.checkError())
            throw new IOException("Write error");
      }
         
      /**
       * Load scores from file.
       * @throws IOException 
       */
      public void load() throws IOException {
         BufferedReader in = new BufferedReader(new FileReader(file));
         List<Score> tmp = new ArrayList<Score>();
         String line;
         while (null != (line = in.readLine())) {
            if (line.startsWith("#"))
               continue;
            int ii = line.indexOf(' ');
            if (ii >= 0) {
               try {
                  tmp.add(new Score(Integer.parseInt(line.substring(0, ii)),
                                    line.substring(ii+1)));
                  continue;
               } catch (NumberFormatException e) {}
            }
            throw new IOException("Bad line in file: " + file + "\n  " + line);
         }
         in.close();
         scores.clear();
         scores.addAll(tmp);
         Collections.sort(scores);
      }

      /**
       * Add a new score and resort and trim the list.  If the score
       * is off the bottom it will be trimmed.
       */
      public Score add(int time, String user) {
         Score sc = new Score(time, user);
         scores.add(sc);
         Collections.sort(scores);
         while (scores.size() > MAX_SCORES)
            scores.remove(scores.size()-1);
         return sc;
      }
   }         
}
