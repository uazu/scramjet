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
import java.util.List;
import java.util.Random;

import net.uazu.con.Area;
import net.uazu.con.CCBuf;
import net.uazu.con.Console;
import net.uazu.con.IAreaUpdateListener;
import net.uazu.con.KP;
import net.uazu.con.KeyEvent;
import net.uazu.event.DelayAction;
import net.uazu.event.Event;
import net.uazu.event.ResizeEvent;
import net.uazu.event.UpdateEvent;
import net.uazu.scramjet.ConsoleTool;
import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.test.Tetronimos.Scores.Score;

/**
 * Tetronimos block-dropping game.  This is a demonstration of an app
 * with a very simple update cycle that doesn't require TiledApp
 * (although it could be used).
 */
public class Tetronimos extends ConsoleTool {
   public Tetronimos(SJContext sjc) { super(sjc); }

   private Area area = Area.empty;

   @Override
   public void setup() {
      da = new DelayAction(con.eloop) {
            public void run() {
               if (active) {
                  down();
                  trigger();
               }
            }
         };
      rand = new Random();
      
      scores = new Scores(
         new File(env.get("HOME") + File.separator +
                  ".scramjet" + File.separator +
                  "tetronimos-scores.txt"));
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

      clearGrid();

      active = false;
   }

   @Override
   public void pass(Event ev, List<Event> out) {
      if (ev instanceof ResizeEvent) {
         con.reinit();
         area = con.newArea();
         area.listener = new IAreaUpdateListener() {
               @Override
               public void areaUpdated(Area area) {
                  eloop.reqUpdate();
               }
            };
         redraw();
         return;
      }
      if (ev instanceof KeyEvent) {
         KeyEvent kev = (KeyEvent) ev;
         key(kev.tag, kev.key);
         return;
      }
      if (ev instanceof UpdateEvent) {
         con.update(area);
         area.updated = false;
         return;
      }
   }

   public void key(KP tag, char key) {
      switch (tag) {
      case C_L:
         con.reinit();
         redraw();
         return;
      case C_C:
         eloop.reqAbort();
         return;
      case Left:
         move(-1);
         return;
      case Right:
         move(1);
         return;
      case Up:
         turn();
         return;
      case Down:
         drop();
         return;
      case KEY:
         key = Character.toLowerCase(key);
         if (active) {
            switch (Character.toLowerCase(key)) {
            case 'q':
               endGame();
               return;
            case 'j':
               move(-1);
               return;
            case 'k':
               turn();
               return;
            case 'l':
               move(1);
               return;
//            case 'h':    // Zoom left
//               while (move(-1)) {}
//               return;
//            case ';':    // Zoom right
//               while (move(1)) {}
//               return;
            case ' ':
               drop();
               return;
            case 'r':
               endGame();
               startGame();
               return;
            }
         } else {
            switch (key) {
            case 'q':
               eloop.reqAbort();
               return;
            case 'n':
            case 'r':
               startGame();
               return;
            }
         }
      }
   }

   /**
    * Logo
    */
   public String[] logo = {
      " r-------i",
      " `--i#r--^-i",
      "    |#|#--#|",
      "  r-<#|#---<",
      "  |#`-+v---^-i",
      "  |#r-'|#r---'",
      "  |#`--<#|",
      "  >----+-^--i",
      "  |#ri#|#ri#|",
      "r-<#`'#|#||#|",
      ">-+----^-^+-'",
      "|#|#ri#ri#|",
      "|#|#||#||#|",
      "`-^-+^-^^v^---i",
      "    |#ri#|##--<",
      "    |#`'#>--##|",
      "    `----^----'"
   };

   /**
    * Surround patterns.
    */
   public String[] surround = {
      "9911199",
      "93",
      "613",
      "1441",
      "23632",
      "466464",
      "5195912",
      "54445554",
      "339933999",
      "1311611411",
      "11126211151",
      "123456765432",
      "6636663666636",
      "54535453545354",
      "123432111234321",
      "4411445544114455",
      "33332222345432222",
      "142536474635241111",
      "9979997999799979997",
      "76267766226676267111"
   };

   /**
    * Pieces with their colours, in all their rotational positions.
    */
   private static final String[] formdata = {
      "11  11  11  11  ",
      "11  11  11  11  ",
      "                ",
      "                ",
      
      "     2       2  ",
      "2222 2  2222 2  ",
      "     2       2  ",
      "     2       2  ",
      
      "     3    3 33  ",
      "333  3  333  3  ",
      "3    33      3  ",
      "                ",
      
      " 44 4    44 4   ",
      "44  44  44  44  ",
      "     4       4  ",
      "                ",
      
      "55   5  55   5  ",
      " 55 55   55 55  ",
      "    5       5   ",
      "                ",

      "     6   6   6  ",
      "666  66 666 66  ",
      " 6   6       6  ",
      "                ",

      "     77 7    7  ",
      "777  7  777  7  ",
      "  7  7      77  ",
      "                ",
   };

   /**
    * Indexes: piece, rotation, row, column.  Values are
    * coloured-chars ready to display.  Empty space has value 'pad'.
    */
   private static int[][][][] form = new int[7][4][4][4];

   /**
    * Value for empty space within form[][][][] and grid[][].
    */
   private static int pad;

   /**
    * Initial y-pos for pieces on dropping.
    */
   private static int[] piece_init_yy = new int[7];

   static {
      pad = Console.gencc(' ', 070);
      for (int a = 0; a<7; a++) {
         for (int b = 0; b<4; b++) {
            for (int c = 0; c<4; c++) {
               for (int d = 0; d<4; d++) {
                  char ch = formdata[a*4 + c].charAt(b*4 + d);
                  form[a][b][c][d] =
                     ch == ' ' ? pad : Console.gencc(' ', 070 | (ch - '0'));
               }
            }
         }
         int yy = 0;
         while (formdata[a*4-yy].startsWith("    "))
            yy--;
         piece_init_yy[a] = yy;
      }
   }

   /**
    * Base scores for clearing 1,2,3,4 lines.  Multiplied by level.
    */
   private static int[] base_scores = { 10, 30, 50, 80 };
   
   /**
    * Grid of settled pieces.  Values are coloured-chars ready to
    * display.
    */
   private int[][] grid = new int[20][10];

   private Area surr_area;
   private Area grid_area;
   private Area logo_area;
   private Area hisc_area;
   private Area help_area;
   
   /**
    * Piece number, rotation and position.
    */
   private int pc_num;
   private int pc_rot;
   private int pc_yy = -4;
   private int pc_xx;

   private DelayAction da;
   private Random rand;
   private Score csc;
   private Score last_score;
   private Scores scores;
   private String scores_error;
   private String user;
   
   /**
    * Game running?
    */
   private boolean active;
   
   /**
    * Redraw.
    */
   public void redraw() {
      area.clr();

      int logo_sx = 19;
      int grid_sx = 24;
      int hisc_sx = Score.header.length();
      int tot_sx = logo_sx + grid_sx + hisc_sx;

      int logo_sy = 21;
      int grid_sy = 21;
      int hisc_sy = 17;

      if (tot_sx > area.cols || grid_sy+2 > area.rows) {
         area.clr(0162);
         area.set(area.rows / 2, (area.cols-16) / 2, 0162, "WINDOW TOO SMALL");
         return;
      }

      int rows = area.rows - 2;
      help_area = new Area(area, area.rows - 1, 0, 1, area.cols, -1);
      
      int pad = (area.cols - tot_sx) / 4;
      int pad2 = (area.cols - tot_sx - pad*2) / 2;

      int xx = pad;
      logo_area = new Area(area, (rows - logo_sy) / 2, xx, logo_sy, logo_sx, -1);
      xx += logo_sx + pad2;
      surr_area = new Area(area, (rows - grid_sy) / 2, xx, grid_sy, grid_sx, -1);
      grid_area = new Area(surr_area, 0, 2, 20, 20, -1);
      xx += grid_sx + pad2;
      hisc_area = new Area(area, (rows - hisc_sy) / 2, xx, hisc_sy, hisc_sx, -1);

      drawHelp();
      drawLogo();
      drawSurround();
      drawGrid();
      drawScores();
   }

   private void drawLogo() {
      double midy = 0.5 * (logo_area.rows-1);
      double midx = 0.5 * (logo_area.cols-1);
      for (int y = logo_area.rows-1; y>=0; y--) {
         for (int x = logo_area.cols-1; x>=0; x--) {
            double dy = y-midy;
            double dx = x-midx;
            int lev = (int) (dy*dy+dx*dx*2);
            if (lev > 240)
               continue;
            int hfb = (240-lev) / 41 * 010;
            logo_area.qset(y, x, Console.gencc(Console.box[12], hfb));
         }
      }
      int yy = 2;
      for (String line : logo) {
         int xx = 2;
         for (char asc : line.toCharArray()) {
            int ch = asc == '#' ? ' ' : Console.ascii2box[asc];
            if (ch != 0) 
               logo_area.set(yy, xx, Console.gencc(ch, 0170));
            xx++;
         }
         yy++;
      }
   }

   private void drawSurround() {
      String patt = csc == null ? "9" :
         surround[(csc.lines / 10) % surround.length];
      int pattlen = patt.length();
      for (int a = 0; a<26; a++) {
         int xx = a<5 ? 10 - 2 * a : 0;
         int yy = a<5 ? 20 : 25-a;
         int hfb = (patt.charAt(a%pattlen) - '0') * 010;
         surr_area.set(yy, xx, hfb, "<>");
         surr_area.set(yy, 22-xx, hfb, "<>");
      }
   }      
   
   /**
    * Clear grid.
    */
   private void clearGrid() {
      for (int a = 0; a<20; a++) {
         for (int b = 0; b<10; b++) {
            grid[a][b] = pad;
         }
      }
   }

   /**
    * Draw the grid area, including the falling piece.
    */
   private void drawGrid() {
      if (grid_area == null)
         return;

      // Redraw whole grid
      for (int a = 0; a<20; a++) {
         int x = 0;
         for (int b = 0; b<10; b++) {
            int cch = grid[a][b];
            grid_area.qset(a, x++, cch);
            grid_area.qset(a, x++, cch);
         }
      }

      // Draw piece on top
      int[][] ff = form[pc_num][pc_rot];
      int y = pc_yy;
      for (int a = 0; a<4; a++) {
         if (y >= 0) {
            int x = pc_xx*2;
            for (int b = 0; b<4; b++) {
               int cch = ff[a][b];
               if (cch == pad) {
                  x += 2;
               } else {
                  grid_area.qset(y, x++, cch);
                  grid_area.qset(y, x++, cch);
               }
            }
         }
         y++;
      }
      grid_area.updated();
   }

   /**
    * Draw the scoreboard.
    */
   private void drawScores() {
      if (scores == null) {
         CCBuf buf = new CCBuf();
         buf.add(0162, scores_error);
         buf.draw(hisc_area, hisc_area.full, 0, true, 0162);
         return;
      }
      
      hisc_area.clr();
      hisc_area.set(0, 0, 0171, Score.header);
      hisc_area.clr(hisc_area.rows-1, 0, 1, hisc_area.cols, Console.gencc(' ', 0171));

      int yy = 1;
      for (Score sc : scores.getScoreBoard(hisc_area.rows-2)) {
         if (sc == null)
            continue;
         int hfb = sc == csc ? 007 : sc == last_score ? 0173 : 070;
         hisc_area.set(yy, 0, hfb, sc.format(hisc_area.cols));
         yy++;
      }
   }

   private void drawHelp() {
      help_area.clr();
      String str = active ?
         "J/K/L/Space or L/U/R/D-arrow: Left,Turn,Right,Drop, R/Q: Restart/Quit Game" :
         "Q: Quit, N: New Game";
      help_area.set(0, (help_area.cols-str.length())/2, 070, str);
   }
   
   /**
    * Test whether piece fits with current settings.
    * @return true: collision, false: okay
    */
   private boolean collision() {
      int[][] ff = form[pc_num][pc_rot];
      for (int a = 0; a<4; a++) {
         for (int b = 0; b<4; b++) {
            if (ff[a][b] == pad)
               continue;
            int x = pc_xx + b;
            if (x < 0 || x >= 10)
               return true;
            int y = pc_yy + a;
            if (y < 0 || y >= 20)
               return true;
            if (grid[y][x] != pad)
               return true;
         }
      }
      return false;
   }

   /**
    * Try to adjust a piece left-right up to 2 places to a place where
    * it doesn't collide with anything.
    * @return true: adjusted successfully, false: failed
    */
   private boolean adjust_piece() {
      int base = pc_xx;
      for (int a = 1; a<3; a++) {
         pc_xx = base + a;
         if (!collision())
            return true;
         pc_xx = base - a;
         if (!collision())
            return true;
      }
      pc_xx = base;
      return false;
   }

   /**
    * Fix piece in this position onto the grid.
    */
   private void fixPiece() {
      int[][] ff = form[pc_num][pc_rot];
      for (int a = 0; a<4; a++) {
         for (int b = 0; b<4; b++) {
            if (ff[a][b] == pad)
               continue;
            int x = pc_xx + b;
            int y = pc_yy + a;
            grid[y][x] = ff[a][b];
         }
      }
   }

   /**
    * Eliminate whole rows.
    */
   private void eliminate() {
      int eliminated = 0;
     outer:
      for (int a = 19; a>=0; a--) {
         for (int b = 0; b<10; b++)
            if (grid[a][b] == pad)
               continue outer;
         eliminated++;
         for (int b = 0; b<10; b++)
            grid[a][b] = pad;
         int[] tmp = grid[a];
         System.arraycopy(grid, 0, grid, 1, a);
         grid[0] = tmp;
         a++;
      }
      if (eliminated != 0 && csc != null && scores != null) {
         csc.lines += eliminated;
         csc.level = csc.lines / 10;
         csc.score += base_scores[eliminated-1] * csc.level;
         scores.resort();
         drawSurround();
         drawScores();
         adjustDelay();
      }
   }

   /**
    * Adjust the delay for falling blocks
    */
   private void adjustDelay() {
      if (csc == null) {
         da.setDelay(300);
         return;
      }
      da.setDelay(300 - Math.min(200, 200 * csc.level / 10));
   }

   /**
    * Start a new piece off.  If its space is blocked, then end the
    * game.
    */
   private void newPiece() {
      pc_num = (rand.nextInt(7) + (0x7FFFFFFF & (int) System.currentTimeMillis())) % 7;
      pc_rot = 0;
      pc_yy = piece_init_yy[pc_num];
      pc_xx = 3;
      if (collision()) {
         pc_yy = -4;   // Hide it off grid
         endGame();
      }
   }

   /**
    * Try to move the given number of relative places horizontally,
    * and redraw.
    * @return true: moved, false: collided
    */
   private boolean move(int cnt) {
      int save = pc_xx;
      pc_xx += cnt;
      if (collision()) {
         pc_xx = save;
         return false;
      }
      drawGrid();
      return true;
   }
   
   /**
    * Try to rotate the piece, and redraw.  Adjusts piece left-right
    * up to two places if it collides with something that would stop
    * it being turned otherwise.
    */
   private void turn() {
      int save = pc_rot;
      pc_rot = (pc_rot+1) & 3;
      if (collision() && !adjust_piece()) {
         pc_rot = save;
      } else {
         drawGrid();
      }
   }

   /**
    * Move piece down, and redraw.  If can't move down any more, fix
    * it in place and start a new piece falling.
    */
   private void down() {
      int save = pc_yy;
      pc_yy++;
      if (collision()) {
         pc_yy = save;
         fixPiece();
         eliminate();
         newPiece();
      }
      drawGrid();
   }

   /**
    * Drop piece down as far as possible, fix it, eliminate full rows,
    * start a new piece falling and redraw.  It is done this way,
    * instead of giving them a bit of extra time to move the piece
    * around, because it makes the game feel faster.
    */
   private void drop() {
      while (true) {
         int save = pc_yy;
         pc_yy++;
         if (collision()) {
            pc_yy = save;
            break;
         }
         if (csc != null)
            csc.score++;
      }
      if (scores != null) {      
         scores.resort();
         drawScores();
      }
      fixPiece();
      eliminate();
      newPiece();
      da.trigger();
      drawGrid();
   }

   /**
    * Start the game
    */
   private void startGame() {
      if (scores != null)
         csc = scores.newCurrent(user);
      last_score = null;
      active = true;
      clearGrid();
      newPiece();
      adjustDelay();
      da.trigger();
      drawGrid();
      drawScores();
      drawHelp();
      drawSurround();
   }

   /**
    * End the game.
    */
   private void endGame() {
      active = false;
      last_score = csc;
      csc = null;
      if (scores != null) {
         scores.noCurrent();
         try {
            scores.save();
         } catch (IOException e) {
            scores = null;
            scores_error = "Can't save scores: " + e.getMessage();
         }
         drawScores();
      }
      drawSurround();
      drawHelp();
   }

   /**
    * High-score table.
    */
   public static class Scores {
      public static class Score implements Comparable<Score> {
         /**
          * Score
          */
         public int score;

         /**
          * Number of lines cleared
          */
         public int lines;

         /**
          * Level
          */
         public int level;

         /**
          * Position in list (at last resort), starting from 1, or -1
          * if off end of list, e.g. current score with low score.
          */
         public int pos;
         
         /**
          * User.
          */
         public final String user;

         public Score(String user) {
            this.user = user;
         }

         /**
          * Sort with highest score first, with number of lines as
          * tie-break.
          */
         public int compareTo(Score bb) {
            return score < bb.score ? 1 :
               score > bb.score ? -1 :
               lines < bb.lines ? 1 :
               lines > bb.lines ? -1 : 0;
         }

         public static String header = 
            "    Score  Lines  Player   ";
         
         public String format(int len) {
            String posstr = pos < 0 ? "--" : String.format("%2d", pos);
            String str = posstr + String.format(
               "%7d%7d  %-40s", score, lines, user);
            return str.substring(0, len);
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
       * Current score, or null
       */
      public Score curr;
      
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
         out.println("# Tetronimos high scores");
         for (Score sc : scores)
            out.println(sc.score + " " + sc.lines + " " + sc.level + " " + sc.user);
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
            int i1 = line.indexOf(' ');
            int i2 = line.indexOf(' ', i1+1);
            int i3 = line.indexOf(' ', i2+1);
            if (i3 >= 0) {
               try {
                  Score sc = new Score(line.substring(i3+1));
                  sc.score = Integer.parseInt(line.substring(0, i1));
                  sc.lines = Integer.parseInt(line.substring(i1+1, i2));
                  sc.level = Integer.parseInt(line.substring(i2+1, i3));
                  tmp.add(sc);
                  continue;
               } catch (NumberFormatException e) {
                  // Drop through to throw
               }
            }
            throw new IOException("Bad line in file: " + file + "\n  " + line);
         }
         in.close();
         scores.clear();
         scores.addAll(tmp);
         resort();
      }

      /**
       * Create a new current Score instance.  This is inserted into
       * the list even if it takes the number over the maximum.  It
       * starts at zero, and it is expected that the application will
       * update it an call {@link #resort} at intervals.  The previous
       * current Score will be lost unless it managed to beat an
       * existing score.
       */
      public Score newCurrent(String user) {
         curr = new Score(user);
         while (scores.size() > MAX_SCORES)
            scores.remove(scores.size()-1);
         scores.add(curr);
         resort();  // Fill in 'pos'
         return curr;
      }

      /**
       * Get the current Score instance.
       */
      public Score getCurrent() {
         return curr;
      }

      /**
       * Resort the list.
       */
      public void resort() {
         Collections.sort(scores);
         int pos = 1;
         for (Score sc : scores) {
            sc.pos = pos > MAX_SCORES ? -1 : pos;
            pos++;
         }
      }

      /**
       * Make the current score into a normal score, which means that
       * it may be dropped if it isn't high enough.
       */
      public void noCurrent() {
         curr = null;
         while (scores.size() > MAX_SCORES)
            scores.remove(scores.size()-1);
      }

      /**
       * Get the top N entries, but always including the current entry
       * if there is one.  Empty entries are null.
       */
      public List<Score> getScoreBoard(int num) {
         List<Score> rv = new ArrayList<Score>();
         boolean has_curr = curr == null;
         int n_scores = scores.size();
         for (int a = 0; a<num; a++) {
            if (a >= n_scores) {
               rv.add(null);
            } else {
               Score sc = scores.get(a);
               if (sc == curr)
                  has_curr = true;
               else if (a == num-1 && !has_curr)
                  sc = curr;
               rv.add(sc);
            }
         }
         return rv;
      }
   }         
}

