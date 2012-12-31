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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.uazu.con.Area;
import net.uazu.con.Console;
import net.uazu.con.IAreaUpdateListener;
import net.uazu.con.KP;
import net.uazu.con.KeyEvent;
import net.uazu.event.Event;
import net.uazu.event.ResizeEvent;
import net.uazu.event.Timer;
import net.uazu.event.UpdateEvent;
import net.uazu.scramjet.ConsoleTool;
import net.uazu.scramjet.SJContext;

/**
 * Full screen coloured clock with calendar and new-moon and full-moon
 * details.  This is a demonstration of an app with a very simple
 * update cycle that doesn't require TiledApp (although it could be
 * used).
 */
public class Clock extends ConsoleTool {
   public Clock(SJContext sjc) { super(sjc); }

   private Area area = Area.empty;
   private Timer timer;

   @Override
   public void pass(Event ev, List<Event> out) {
      if (ev instanceof ResizeEvent) {
         con.reinit();
         area = con.newArea();
         area.listener = new IAreaUpdateListener() {
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
      if (tag == KP.C_L) {
         con.reinit();
         redraw();
         return;
      }
      if (tag == KP.C_C) {
         eloop.reqAbort();
         return;
      }
      if (tag == KP.KEY && (key == 'q' || key == 'Q')) {
         eloop.reqAbort();
         return;
      }
   }

   /**
    * Returns time that next redraw is required
    */
   public long redraw() {
      // Setup calendar
      Calendar cal = new GregorianCalendar();
      long now = System.currentTimeMillis();
      cal.setTimeInMillis(now);

      if (area != Area.empty) {
         int cal_hgt = area.rows >= 24 ? 10 : area.rows >= 22 ? 9 : 8;
         
         // Draw clock
         area.clear(clkbg);
         drawClock(0, 0, area.rows-cal_hgt, area.cols, cal);
         
         // Draw current month
         int midp = area.cols/2-10;
         int yy = area.rows - cal_hgt;
         drawMonth(yy, midp, cal, cal.get(Calendar.DAY_OF_MONTH));
         
         // Draw as many previous and following months as possible,
         // same number both sides
         for (int a = 1; true; a++) {
            int x1 = midp - 23 * a;
            int x2 = midp + 23 * a;
            if (x1 < 1 && x2 > area.cols - 21)
               break;

            cal.setTimeInMillis(now);
            cal.set(Calendar.DAY_OF_MONTH, 15);
            cal.add(Calendar.MONTH, -a);
            cal.getTimeInMillis();
            drawMonth(yy, x1, cal, 0);
            
            cal.setTimeInMillis(now);
            cal.set(Calendar.DAY_OF_MONTH, 15);
            cal.add(Calendar.MONTH, a);
            cal.getTimeInMillis();
            drawMonth(yy, x2, cal, 0);
         }
      }

      // Calculate delay before next redraw
      cal.setTimeInMillis(now);
      cal.set(Calendar.SECOND, 0);
      cal.add(Calendar.MINUTE, 1);
      long redraw_time = cal.getTimeInMillis();
      
      // Setup timer if not yet setup
      if (timer == null) {
         eloop.addTimer(new Timer(redraw_time) {
               public long run(long req, long now) {
                  return redraw();
               }
            });
      }
      return redraw_time;
   }

   private static String[] WEEKDAY = {
      "Sunday", "Monday", "Tuesday", "Wednesday",
      "Thursday", "Friday", "Saturday", "Sunday"
   };

   private static final String[] MON = {
      "Jan", "Feb", "Mar", "Apr", "May", "Jun",
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
   };
   
   private static final String[] MONTH = {
      "January", "February", "March", "April",
      "May", "June", "July", "August",
      "September", "October", "November", "December"
   };

   // Colours
   private static final int clkbg = 072;
   private static final int clkfg = 0177;
   private static final int calbg = 0172;
   private static final int calhi = 0171;
   private static final int calfull = 0152;
   private static final int calnew = 0142;

   /**
    * Draw a month from the calendar.  Requires 20 columns, and 8
    * lines, with a recommended horizontal spacing of 3 columns
    * between months.  The current day-of-the month `hday' may be
    * highlighted, or pass 0 if not required.  The background should
    * have been cleared beforehand.  The Calendar passed will have
    * been modified on return.
    */
   private void drawMonth(int yy, int xx, Calendar cal, int hday) {
      String str = MONTH[cal.get(Calendar.MONTH) - Calendar.JANUARY] + " " +
            cal.get(Calendar.YEAR);
      area.set(yy++, xx+(20-str.length())/2, calbg, str);
      area.set(yy++, xx, calbg, "Mo Tu We Th Fr Sa Su");
      
      int mon= cal.get(Calendar.MONTH);
      cal.set(Calendar.DAY_OF_MONTH, 1);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.MINUTE, 0);
      // Use 12 midday to avoid problems when clock goes forwards or back
      cal.set(Calendar.HOUR_OF_DAY, 12);
      while (true) {
         long tim = cal.getTimeInMillis();
         if (mon != cal.get(Calendar.MONTH))
            break;
         long[] ss = get_moons(tim-12*3600*1000);	// Start of day
         long[] es = get_moons(tim+12*3600*1000);	// End of day
         boolean znew= ss[1] != es[1];
         boolean zfull= tim-12*3600*1000 < ss[1] && tim+12*3600*1000 > ss[1];

         // Put Sunday at the end
         int wday= (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 13) % 7;
         int mday = cal.get(Calendar.DAY_OF_MONTH);
         area.set(yy, xx + 3*wday,
                  mday == hday ? calhi :
                  zfull ? calfull : znew ? calnew : calbg, 
                  String.format("%2d", mday));

         cal.set(Calendar.DAY_OF_MONTH, mday+1);
         if (wday == 6) yy++;
      }
   }
   
   /**
    * Draw a clock centred in the given region, using extra-big
    * digits, big digits or small digits according to the size.
    * Display is time hh:mm AM/PM, plus day-of-week and date.  Minimum
    * size is 7 lines high, and 60 characters wide.
    */
   private void drawClock(int yy, int xx, int sy, int sx, Calendar cal) {
      boolean use_xbig = sy >= 18;
      boolean use_big = sy >= 11;
      int adj= -1;			// Just lose the padding space at the start of the `1'
      Digit[] dig = use_xbig ? xbig : use_big ? big : sma;
      int hgt = dig[0].sy;
      
      Digit[] str = new Digit[5];
      int hour = (cal.get(Calendar.HOUR_OF_DAY) + 11) % 12 + 1;
      int min = cal.get(Calendar.MINUTE);
      str[0]= dig[(hour >= 10) ? 1 : 10];
      str[1]= dig[hour % 10];
      str[2]= dig[11];
      str[3]= dig[min / 10];
      str[4]= dig[min % 10];
      
      int twid= (use_xbig ? 14 : 10) + adj;    // 10/14 for stuff at end
      for (Digit dd : str)
         twid += dd.sx;
      
      boolean sqz = twid > sx;
      if (sqz) twid--;
      
      yy += (sy-hgt)/2;
      xx += (sx-twid)/2 + adj;

      int cch = Console.gencc('#', clkfg);
      for (Digit dd : str) 
         xx += dd.draw(area, yy, xx, cch);
      
      int pm = (cal.get(Calendar.HOUR_OF_DAY) >= 12) ? 1 : 0;
      if (!sqz) xx++;
      
      if (!use_xbig) {
         String[] moon = moon_string2();
         area.set(yy+1, xx, clkbg, ampm_txt2[pm][0]);
         area.set(yy+2, xx, clkbg, ampm_txt2[pm][1]);
         area.set(yy+hgt - 4, xx, clkbg, moon[0]);
         area.set(yy+hgt - 3, xx, clkbg, moon[1]);
         area.set(yy+hgt - 2, xx, clkbg,
                  WEEKDAY[(cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 14) % 7]);
         area.set(yy+hgt - 1, xx, clkbg,
                  String.format("%s %d", MON[cal.get(Calendar.MONTH)],
                                cal.get(Calendar.DAY_OF_MONTH)));
      } else {
         String[] moon = moon_string4();
         area.set(yy+1, xx, clkbg, ampm_txt3[pm][0]);
         area.set(yy+2, xx, clkbg, ampm_txt3[pm][1]);
         area.set(yy+3, xx, clkbg, ampm_txt3[pm][2]);
         area.set(yy+6, xx, clkbg,
                  WEEKDAY[(cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 14) % 7]);
         area.set(yy+7, xx, clkbg,
                  String.format("%s %d", MONTH[cal.get(Calendar.MONTH)],
                                cal.get(Calendar.DAY_OF_MONTH)));
         for (int a= 0; a<4; a++)
            area.set(yy+hgt-4+a, xx, clkbg, moon[a]);
         area.set(yy+hgt-3, xx + 14, calnew, "N");
         area.set(yy+hgt-2, xx + 14, calfull, "F");
         area.set(yy+hgt-1, xx + 14, calnew, "N");
      }
   }

   /**
    * Digit for drawing
    */
   private static class Digit {
      private final int[] bitmap;
      public final int sx, sy;
      public Digit(String[] in, int xx, int yy, int sx, int sy) {
         bitmap = new int[sy];
         this.sx = sx;
         this.sy = sy;
         for (int ox = 0; ox < sx; ox++) {
            for (int oy = 0; oy < sy; oy++) {
               if (in[yy + oy].charAt(xx + ox) != ' ')
                  bitmap[oy] |= 1<<ox;
            }
         }
      }
      public int draw(Area area, int yy, int xx, int cch) {
         for (int oy = 0; oy < sy; oy++) {
            int bm = bitmap[oy];
            for (int ox = 0; ox < sx; ox++) {
               if (0 != (bm & 1))
                  area.set(yy + oy, xx + ox, cch);
               bm >>= 1;
            }
         }
         return sx;
      }
   }

   /**
    * Generate Digit[] from raw data
    */
   static Digit[] gen_digits(String chars, String... data) {
      int lines = 0;
      int rows = 0;
      for (String line : data) {
         if (line.startsWith("%"))
            rows++;
         else 
            lines++;
      }
      int sy = lines/rows;

      int len = chars.length();
      Digit[] rv = new Digit[len];
      for (int a = 0; a<len; a++) {
         char ch = chars.charAt(a);
         for (int b = 0; true; b += sy + 1) {
            int ox1 = data[b].indexOf(ch);
            if (ox1 < 0) continue;
            int ox2 = data[b].lastIndexOf(ch);
            rv[a] = new Digit(data, ox1, b+1, ox2-ox1+1, sy);
            break;
         }
      }
      return rv;
   }
            
   /**
    * Extra-large digits
    */
   private static Digit[] xbig = gen_digits(
      "0123456789-:",
      "%000000000000000000111111111111122222222222222222233333333333333333",
      "     #########          ###       ###########       ##########     ",
      "   #############      #####      ##############    #############   ",
      "  #####     #####   #######                #####            #####  ",
      "  ####       ####   #######                 ####             ####  ",
      "  ####       ####      ####                 ####             ####  ",
      "  ####       ####      ####                #####            ####   ",
      "  ####       ####      ####         ###########      ##########    ",
      "  ####       ####      ####       ###########        ##########    ",
      "  ####       ####      ####      #####                      ####   ",
      "  ####       ####      ####      ####                        ####  ",
      "  ####       ####      ####      ####                        ####  ",
      "  #####     #####      ####      ####                       #####  ",
      "   #############    ##########   ###############   #############   ",
      "     #########      ##########   ###############    ##########     ",
      
      "%4444444444444444445555555555555555556666666666666666667777777777777777",
      "  ####     ####     ##############       ##########     #############  ",
      "  ####     ####     ##############     #############    #############  ",
      "  ####     ####     ####              #####                      ####  ",
      "  ####     ####     ####              ####                       ####  ",
      "  ####     ####     ####              ####                      ####   ",
      "  ####     ####     ####              ####                      ####   ",
      "  ###############   ############      ############              ####   ",
      "   ##############   ##############    ##############           ####    ",
      "           ####               #####   ####      #####          ####    ",
      "           ####                ####   ####       ####          ####    ",
      "           ####                ####   ####       ####         ####     ",
      "           ####               #####   #####     #####         ####     ",
      "           ####     ##############     #############          ####     ",
      "           ####      ###########         #########            ####     ",
      
      "%8888888888888888888999999999999999999--::::::",
      "     ##########         #########             ",
      "   ##############     #############           ",
      "  #####      #####   #####     #####          ",
      "  ####        ####   ####       ####          ",
      "  ####        ####   ####       ####     ###  ",
      "   ####      ####    #####      ####     ###  ",
      "    ############      ##############          ",
      "    ############        ############          ",
      "   ####      ####               ####     ###  ",
      "  ####        ####              ####     ###  ",
      "  ####        ####              ####          ",
      "  #####      #####             #####          ",
      "   ##############     #############           ",
      "     ##########        ##########             "
   );

   /**
    * Big digits.
    */
   private static Digit[] big = gen_digits(
      "0123456789-:",
      "%00000000000011111111122222222222233333333333444444444444-",
      "   ########     ###     ########   ########   ###    ###  ",
      "  ###    ###  #####           ###        ###  ###    ###  ",
      "  ###    ###    ###           ###        ###  ###    ###  ",
      "  ###    ###    ###           ###        ###  ###    ###  ",
      "  ###    ###    ###     ########   ########    #########  ",
      "  ###    ###    ###    ###               ###         ###  ",
      "  ###    ###    ###    ###               ###         ###  ",
      "  ###    ###    ###    ###               ###         ###  ",
      "   ########   #######   ########   ########          ###  ",
      
      "%55555555555566666666666677777777777888888888888999999999999::::",
      "   ########    ########   ########    ########    ########      ",
      "  ###         ###               ###  ###    ###  ###    ###     ",
      "  ###         ###               ###  ###    ###  ###    ###  ## ",
      "  ###         ###               ###  ###    ###  ###    ###  ## ",
      "   ########   #########         ###   ########    #########     ",
      "         ###  ###    ###        ###  ###    ###         ###  ## ",
      "         ###  ###    ###        ###  ###    ###         ###  ## ",
      "         ###  ###    ###        ###  ###    ###         ###     ",
      "   ########    ########         ##    ########    ########      "
   );

   /**
    * Small digits
    */
   private static Digit[] sma = gen_digits(
      "0123456789-:",
      "%00000000000011111111122222222222233333333333444444444444-",
      "   ########     ###     ########   ########   ###    ###  ",
      "  ###    ###  #####           ###        ###  ###    ###  ",
      "  ###    ###    ###           ###        ###  ###    ###  ",
      "  ###    ###    ###     ########   ########    #########  ",
      "  ###    ###    ###    ###               ###         ###  ",
      "  ###    ###    ###    ###               ###         ###  ",
      "   ########   #######   ########   ########          ###  ",
      
      "%55555555555566666666666677777777777888888888888999999999999::::",
      "   ########    ########   ########    ########    ########      ",
      "  ###         ###               ###  ###    ###  ###    ###     ",
      "  ###         ###               ###  ###    ###  ###    ###  ## ",
      "   ########   #########         ###   ########    #########     ",
      "         ###  ###    ###        ###  ###    ###         ###  ## ",
      "         ###  ###    ###        ###  ###    ###         ###     ",
      "   ########    ########         ##    ########    ########      "
   );

   private static String[][] ampm_txt2 = {
      { " /||\\/|",      //   /||\/|
        "/ ||  |" },     //  / ||  |
      { "| )|\\/|",      //            | )|\/|
        "|  |  |" }      //            |  |  |
   };
   
   private static String[][] ampm_txt3= {
      { "  /||\\ /|",    //    /||\ /|
        " /_|| ~ |",     //   /_|| ~ |
        "/  ||   |" },   //  /  ||   |
      { "|  \\|\\ /|",   //             |  \|\ /|
        "| _/| ~ |",     //             | _/| ~ |
        "|   |   |" },   //             |   |   |
   };
   

   /***********************************************************************

    A Moon for OpenWindows

        Release 3.0

    Designed and implemented by John Walker in December 1987,
    Revised and updated in February of 1988.
    Revised and updated again in June of 1988 by Ron Hitchens.
    Revised and updated yet again in July/August of 1989 by Ron Hitchens.
    Converted to OpenWindows in December of 1991 by John Walker.

    This  program  is  an OpenWindows tool which displays, as the icon
    for a closed window, the current phase of the Moon.  A subtitle in
    the  icon  gives the age of the Moon in days and hours.  If called
    with the "-t" switch, it rapidly increments forward  through  time
    to display the cycle of phases.

    If you  open  the  window, additional  information  is  displayed
    regarding  the  Moon.   The  information  is generally accurate to
    within ten minutes.

    The algorithms used in this program to calculate the positions Sun
    and  Moon  as seen from the Earth are given in the book "Practical
    Astronomy With Your Calculator"  by  Peter  Duffett-Smith,  Second
    Edition,  Cambridge  University  Press,  1981.   Ignore  the  word
    "Calculator" in the title;  this  is  an  essential  reference  if
    you're   interested   in   developing  software  which  calculates
    planetary positions, orbits, eclipses, and the  like.   If  you're
    interested in  pursuing such programming, you should also obtain:

    "Astronomical Formulae  for  Calculators"  by  Jean  Meeus,  Third
    Edition, Willmann-Bell, 1985.  A must-have.

    "Planetary  Programs  and  Tables  from  -4000 to +2800" by Pierre
    Bretagnon and Jean-Louis Simon, Willmann-Bell, 1986.  If you  want
    the  utmost  (outside of JPL) accuracy for the planets, it's here.

    "Celestial BASIC" by Eric Burgess, Revised Edition,  Sybex,  1985.
    Very cookbook oriented, and many of the algorithms are hard to dig
    out of the turgid BASIC code, but you'll probably want it anyway.

    Many  of these references can be obtained from Willmann-Bell, P.O.
    Box 35025, Richmond, VA 23235, USA.  Phone:  (804) 320-7016.   In
    addition  to  their  own  publications,  they  stock  most of the
    standard references for mathematical and positional astronomy.

    This program was written by:

 John Walker
 Autodesk Neuchbtel
 Avenue des Champs-Montants 14b
 CH-2074 MARIN
 Switzerland
 Usenet: kelvin@Autodesk.com
 Fax: 038/33 88 15
 Voice: 038/33 76 33

    This  program is in the public domain: "Do what thou wilt shall be
    the whole of the law".  I'd appreciate  receiving  any  bug  fixes
    and/or  enhancements, which I'll incorporate in future versions of
    the program.  Please leave the  original  attribution  information
    intact so that credit and blame may be properly apportioned.

 History:
 --------
 June 1988 Version 2.0 posted to usenet by John Walker

 June 1988 Modified by Ron Hitchens
        ronbo@vixen.uucp
        ...!uunet!cs.utah.edu!caeco!vixen!ronbo
        hitchens@cs.utexas.edu
   to produce version 2.1.
   Modified icon generation to show surface texture
    on visible moon face.
   Added a menu to allow switching in and out of
    test mode, for entertainment value mostly.
   Modified layout of information in open window display
    to reduce the amount of pixels modified in each
    update.

 July 1989 Modified further for color displays.  On a color Sun,
    four colors will be used for the canvas and the icon.
    Rather than just show the illuminated portion of
    the moon, a color icon will also show the darkened
    portion in a dark blue shade. The text on the icon
                         will also be drawn in a nice "buff" color, since there
    was one more color left to use.
                        Add two command line args, "-c" and "-m" to explicitly
    specify color or monochrome mode.
   Use getopt to parse the args.
   Change the tool menu slightly to use only one item
    for switching in and out of test mode.

 July 1989 Modified a little bit more a few days later to use 8
    colors and an accurate grey-scale moon face created
    by Joe Hitchens on an Amiga.
   Added The Apollo 11 Commemorative Red Dot, to show
    where Neil and Buzz went on vacation a few years ago.
   Updated man page.

        August 1989     Received version 2.3 of John Walker's original code.
   Rolled in bug fixes to astronomical algorithms:

                         2.1  6/16/88   Bug fix.  Table of phases didn't update
     at the moment of the new moon. Call on
                                        phasehunt  didn't  convert civil Julian
     date  to  astronomical  Julian   date.
     Reported by Dag Bruck
      (dag@control.lth.se).

    2.2  N/A (Superseded by new moon icon.)

    2.3  6/7/89 Bug fix.  Table of phases  skipped  the
     phases for  July  1989.  This occurred
     due  to  sloppy  maintenance   of   the
     synodic  month index in the interchange
     of information between phasehunt()  and
     meanphase().  I simplified and
     corrected  the handling  of  the month
     index as phasehunt()  steps  along  and
     removed unneeded code from meanphase().
     Reported by Bill Randle  of  Tektronix.
      (billr@saab.CNA.TEK.COM).

 January 1990 Ported to OpenWindows by John Walker.
                        All  of Ron Hitchens' additions which were not
   Sun-specific  were   carried   on   into   the
   OpenWindows version.

 September 1993 reported to Motif (as God intended) by Cary Sandvig.
   Some window reformatting was done as I was unable to
   view the existing setup.

 April 1997 Ported to XView under Linux by Joerg Richter.
   Added moon rise/set feature (based on code of
   Marc T. Kaufman)
*/

   //  Astronomical constants  
   private static final double epoch = 2444238.5;    // 1980 January 0.0 
   
   // ---- Constants defining the Sun's apparent orbit
   // Ecliptic longitude of the Sun at epoch 1980.0 
   private static final double elonge = 278.833540;
   // Ecliptic longitude of the Sun at perigee 
   private static final double elongp = 282.596403;
   // Eccentricity of Earth's orbit 
   private static final double eccent = 0.016718;
   // Semi-major axis of Earth's orbit, km 
   //private static final double sunsmax = 1.495985e8;
   // Sun's angular size, degrees, at semi-major axis distance 
   //private static final double sunangsiz = 0.533128;

   // ---- Elements of the Moon's orbit, epoch 1980.0
   // Moon's mean lonigitude at the epoch 
   private static final double mmlong = 64.975464;
   // Mean longitude of the perigee at the epoch 
   private static final double mmlongp = 349.383063;
   // Mean longitude of the node at the epoch 
   //private static final double mlnode = 151.950429;
   // Inclination of the Moon's orbit 
   //private static final double minc = 5.145396;
   // Eccentricity of the Moon's orbit 
   //private static final double mecc = 0.054900;
   // Moon's angular size at distance a from Earth 
   //private static final double mangsiz = 0.5181;
   // Semi-major axis of Moon's orbit in km 
   //private static final double msmax = 384401.0;
   // Parallax at distance a from Earth 
   //private static final double mparallax = 0.9507;
   // Synodic month (new Moon to new Moon) 
   private static final double synmonth = 29.53058868;
   // // Base date for E. W. Brown's numbered series of lunations (1923 January 16) 
   // private static final double lunatbase = 2423436.0;
   
   // ---- Properties of the Earth
   // // Radius of Earth in kilometres 
   // private static final double earthrad = 6378.16;

   // ---- Handy mathematical functions
   // Fix angle
   private static double fixangle(double a) {
      return ((a) - 360.0 * (Math.floor((a) / 360.0)));
   }
   // Deg->Rad   
   private static double torad(double d) {
      return ((d) * (Math.PI / 180.0));
   }
   // Rad->Deg   
   private static double todeg(double d) {
      return ((d) * (180.0 / Math.PI));
   }

   /**
    * JTIME -- Convert internal GMT date and time to astronomical
    * Julian time (i.e. Julian date plus day fraction, expressed as a
    * double).
    */
   private double jtime(long time) {
      GregorianCalendar cal = new GregorianCalendar(
         TimeZone.getTimeZone("GMT+0000"), Locale.UK);
      cal.setTimeInMillis(time);
      
      int y = cal.get(Calendar.YEAR);
      int m = cal.get(Calendar.MONTH) - Calendar.JANUARY + 1;
      if (m > 2) {
         m = m - 3;
      } else {
         m = m + 9;
         y--;
      }
      int c = y / 100;        /* Compute century */
      y -= 100 * c;
      long jdate = (cal.get(Calendar.DAY_OF_MONTH) +
                   (c * 146097L) / 4 +
                   (y * 1461L) / 4 +
                   (m * 153L + 2) / 5 +
                   1721119L);
      double rv =  jdate - 0.5 + 
         (cal.get(Calendar.SECOND) +
          60 * (cal.get(Calendar.MINUTE) +
                60 * cal.get(Calendar.HOUR_OF_DAY))) / 86400.0;
      return rv;
   }
   
   /**
    * KEPLER  --  Solve the equation of Kepler.
    */
   private double kepler(double m, double ecc) {
      double e, delta;
      final double EPSILON = 1E-6;
      
      e = m = torad(m);
      do {
         delta = e - ecc * Math.sin(e) - m;
         e -= delta / (1 - ecc * Math.cos(e));
      } while (Math.abs(delta) > EPSILON);
      return e;
   }

   private class PhaseRV {
      double  phase;
      double  pphase;      /* Illuminated fraction */
      double  mage;        /* Age of moon in days */
//      double  dist;        /* Distance in kilometres */
//      double  angdia;      /* Angular diameter in degrees */
//      double  sudist;      /* Distance to Sun */
//      double  suangdia;    /* Sun's angular diameter */
   }
   
   /**
    * PHASE  --  Calculate phase of moon as a fraction:
    *   
    * <p>The argument is the time for which the phase is requested,
    * expressed as a Julian date and fraction.  Returns the terminator
    * phase angle as a percentage of a full circle (i.e., 0 to 1), and
    * stores into pointer arguments the illuminated fraction of the
    * Moon's disc, the Moon's age in days and fraction, the distance
    * of the Moon from the centre of the Earth, and the angular
    * diameter subtended by the Moon as seen by an observer at the
    * centre of the Earth.
    */
   private PhaseRV phase(double pdate) {
      double Day, N, M, Ec, Lambdasun, ml, MM, Ev, Ae, A3, MmP,
         mEc, A4, lP, V, lPP,
         MoonAge, MoonPhase;
      //double MoonDist, MoonDFrac, MoonAng, F, SunDist, SunAng;
      
      /* Calculation of the Sun's position */
      
      Day = pdate - epoch;      /* Date within epoch */
      N = fixangle((360 / 365.2422) * Day);   /* Mean anomaly of the Sun */
      M = fixangle(N + elonge - elongp);     /* Convert from perigee
                                                co-ordinates to epoch 1980.0 */
      Ec = kepler(M, eccent);      /* Solve equation of Kepler */
      Ec = Math.sqrt((1 + eccent) / (1 - eccent)) * Math.tan(Ec / 2);
      Ec = 2 * todeg(Math.atan(Ec));      /* True anomaly */
      Lambdasun = fixangle(Ec + elongp);      /* Sun's geocentric ecliptic
                                                 longitude */
      /* Orbital distance factor */
      //F = ((1 + eccent * Math.cos(torad(Ec))) / (1 - eccent * eccent));
      //SunDist = sunsmax / F;      /* Distance to Sun in km */
      //SunAng = F * sunangsiz;                 /* Sun's angular size in degrees */
      
      /* Calculation of the Moon's position */
      
      /* Moon's mean longitude */
      ml = fixangle(13.1763966 * Day + mmlong);
      
      /* Moon's mean anomaly */
      MM = fixangle(ml - 0.1114041 * Day - mmlongp);
      
      /* Moon's ascending node mean longitude */
      //MN = fixangle(mlnode - 0.0529539 * Day);
      
      /* Evection */
      Ev = 1.2739 * Math.sin(torad(2 * (ml - Lambdasun) - MM));

      /* Annual equation */
      Ae = 0.1858 * Math.sin(torad(M));
      
      /* Correction term */
      A3 = 0.37 * Math.sin(torad(M));
      
      /* Corrected anomaly */
      MmP = MM + Ev - Ae - A3;
      
      /* Correction for the equation of the centre */
      mEc = 6.2886 * Math.sin(torad(MmP));
      
      /* Another correction term */
      A4 = 0.214 * Math.sin(torad(2 * MmP));
      
      /* Corrected longitude */
      lP = ml + Ev + mEc - Ae + A4;
      
      /* Variation */
      V = 0.6583 * Math.sin(torad(2 * (lP - Lambdasun)));
      
      /* True longitude */
      lPP = lP + V;
      
      /* Corrected longitude of the node */
      //NP = MN - 0.16 * Math.sin(torad(M));
      
      /* Y inclination coordinate */
      //y = Math.sin(torad(lPP - NP)) * Math.cos(torad(minc));
      
      /* X inclination coordinate */
      //x = Math.cos(torad(lPP - NP));
      
      /* Ecliptic longitude */
      //Lambdamoon = todeg(Math.atan2(y, x));
      //Lambdamoon += NP;
      
      /* Ecliptic latitude */
      //BetaM = todeg(Math.asin(Math.sin(torad(lPP - NP)) * Math.sin(torad(minc))));
      
      /* Calculation of the phase of the Moon */
      
      /* Age of the Moon in degrees */
      MoonAge = lPP - Lambdasun;
      
      /* Phase of the Moon */
      MoonPhase = (1 - Math.cos(torad(MoonAge))) / 2;
      
      /* Calculate distance of moon from the centre of the Earth */
      
      //MoonDist = (msmax * (1 - mecc * mecc)) /
      //   (1 + mecc * Math.cos(torad(MmP + mEc)));

      /* Calculate Moon's angular diameter */
      
      //MoonDFrac = MoonDist / msmax;
      //MoonAng = mangsiz / MoonDFrac;
      
      /* Calculate Moon's parallax */
      //MoonPar = mparallax / MoonDFrac;

      PhaseRV rv = new PhaseRV();
      rv.phase = fixangle(MoonAge) / 360.0;
      rv.pphase = MoonPhase;
      rv.mage = synmonth * (fixangle(MoonAge) / 360.0);
      //rv.dist = MoonDist;
      //rv.angdia = MoonAng;
      //rv.sudist = SunDist;
      //rv.suangdia = SunAng;
      return rv;
   }

   /**
    * Look for the following new moon the slow but guaranteed way;
    * requires 16 calls to phase() to give ~30s accuracy.
    */
   private long 
   find_new_moon(long now) {
      double aom;
      long t0, t1, t2;
      int cnt;

      PhaseRV pr = phase(jtime(now));
      aom = pr.mage;
      
      //DEBUG System.err.print(String.format("find_new_moon %d -> aom == %g\r\n", now/1000, aom));
      
      t0= now + (long)((25 - aom) * 24 * 3600 * 1000);
      t2= now + (long)((35 - aom) * 24 * 3600 * 1000);
      
      for (cnt= 15; cnt > 0; cnt--) { // 14 -> 10days*(0.5**14) -> ~1 minute accuracy
         t1= t0 + (t2-t0)/2; // (t0+t2)/2 overflows!
         pr = phase(jtime(t1));
         aom = pr.mage;
         
         //DEBUG System.err.print(String.format("  %g %g %g -> aom == %g\r\n",
         //DEBUG                                (t0-now) / (24.0*3600.0*1000),
         //DEBUG                                (t1-now) / (24.0*3600.0*1000),
         //DEBUG                                (t2-now) / (24.0*3600.0*1000),
         //DEBUG                                aom));
                          
         if (aom < 15) 
            t2= t1;
         else 
            t0= t1;
      }
      
      //DEBUG  System.err.print(String.format("find_new_moon -> aom == %g\r\n" +
      //DEBUG                                "  returns %d\r\n", aom, (t0+(t2-t0)/2) / 1000));
                       
      return t0 + (t2-t0)/2;
   }

   /**
    * Find the full moon between two new moons; really slow way (72
    * phase calls).
    */
   private long find_full_moon(long new0, long new1) {
      long t0, t1, t2, t3;
      double cp1, cp2;
      int cnt;
      PhaseRV pr;
      
      t0= new0;
      t3= new1;
      
      for (cnt= 28; cnt > 0; cnt--) { // 27 -> 30days*(0.6666**27) -> ~1 minute accuracy
         t1= t0 + (t3-t0)/3;
         t2= t3 - (t3-t0)/3;
         pr= phase(jtime(t1));
         cp1 = pr.pphase;
         pr= phase(jtime(t2));
         cp2 = pr.pphase;
         
         if (cp1 > cp2)
            t3= t2;
         else 
            t0= t1;
      }
      
      return t0 + (t3-t0)/2;
   }

   private int n_nm= 0;
   private long[] newmoon = new long[64]; // newmoon[0..n_nm-1] valid
   private long[] fullmoon = new long[64]; // fullmoon[0..n_nm-2] valid
   private final int m_nm= 64;
   
   /**
    * Given a time, return the previous and next new moons, and the
    * full moon between them.  Results are accurate to about a minute.
    * Caches results for speed.
    */
   private long[] get_moons(long time) {
      int a;
      
      // Forget current cache if it is more than 32 lunar months (roughly) away
      if (n_nm > 0 && (time < newmoon[0] - 32L*30*24*3600*1000 ||
                       time > newmoon[n_nm-1] + 32L*30*24*3600*1000))
         n_nm= 0;
      
      // Start the array off
      if (n_nm == 0) {
         newmoon[0]= find_new_moon(time);
         n_nm= 1;
      }
      
      // Search backwards
      while (time < newmoon[0]) {
         System.arraycopy(newmoon, 0, newmoon, 1, m_nm-1);
         System.arraycopy(fullmoon, 0, fullmoon, 1, m_nm-1);
         if (n_nm < m_nm) n_nm++;
         newmoon[0]= find_new_moon(newmoon[1] - 40L*24*3600*1000);
         fullmoon[0]= find_full_moon(newmoon[0], newmoon[1]);
      }
      
      // Search forwards
      while (time > newmoon[n_nm-1]) {
         if (n_nm == m_nm) {
            System.arraycopy(newmoon, 1, newmoon, 0, m_nm-1);
            System.arraycopy(fullmoon, 1, fullmoon, 0, m_nm-1);
         } else {
            n_nm++;
         }
         newmoon[n_nm-1]= find_new_moon(newmoon[n_nm-2] + 20L*24*3600*1000);
         fullmoon[n_nm-2]= find_full_moon(newmoon[n_nm-2], newmoon[n_nm-1]);
      }
      
      // Find enclosing range
      for (a= 0; a<n_nm-1; a++) 
         if (time >= newmoon[a] && time <= newmoon[a+1])
            break;
      
      if (a >= n_nm-1)
         throw new RuntimeException("Internal error in get_moons()");

      return new long[] { newmoon[a], fullmoon[a], newmoon[a+1] };
   }   

   /**
    * Check the number of days away from the previous and next new
    * moon from the given time.  Caches a number of new moon times, so
    * this is efficient.
    */
   //private static double[] check_days(long time) {
   //   long[] rv = get_moons(time);
   //   return new double[] { (time-rv[0]) / (24.0*3600.0*1000.0),
   //                         (rv[1]-time) / (24.0*3600.0*1000.0) };
   //}

   /**
    * Write a day-time as 4 characters, in one of the forms: "25.1",
    * "20h", "8.2h", "33m".
    */
   private String daytime(double tim) {
      if (tim > 1.0) 
         return String.format("%4.1f", tim);
      
      tim *= 24;
      if (tim >= 9.9) 
         return String.format("%3dh", (int)tim);
      if (tim > 1.0) 
         return String.format("%3.1fh", tim);
      
      tim *= 60;
      if (tim > 0) 
         return String.format("%3dm", (int)tim);
      else 
         return "   0";
   }

   
   /**
    * Write a date/time in localtime format "Jan-12 14:34"
    */
   private String wrdate(long tim) {
      Calendar cal = new GregorianCalendar();
      cal.setTimeInMillis(tim);
      return String.format(
         "%s-%02d %02d:%02d",
         MON[cal.get(Calendar.MONTH) - Calendar.JANUARY],
         cal.get(Calendar.DAY_OF_MONTH),
         cal.get(Calendar.HOUR_OF_DAY),
         cal.get(Calendar.MINUTE));
   }
   
   private long new_moon= 0;

   /**
    * Generates new moon-strings (two-line version)
    */
   private String[] moon_string2() {
      long now;
      double p, aom, cphase;
      
      now= System.currentTimeMillis();

      PhaseRV pr= phase(jtime(now));
      p = pr.phase;
      cphase = pr.pphase;
      aom = pr.mage;
      
      if (new_moon < now ||
          (p < 0.5 && new_moon-now > 35L*24*3600*1000) ||
          (p >= 0.5 && new_moon-now > 20L*24*3600*1000))
         new_moon= find_new_moon(now);

      String str1 = "Moon: " + daytime(aom);
      String str2 = String.format(
         "%3d%c  ",
         (int)(Math.floor(cphase * 90 + 0.5)),
         (p < 0.5) ? '+' : '-') +
         daytime((new_moon-now) * (1.0 / 24 / 3600 / 1000));
      return new String[] { str1, str2 };
   }

//
// Generates new moon-strings in the given destination string
// buffers
//

   private String[] moon_string4() {
      long new0, full, new1;
      long now;
      double p, aom, cphase;
      
      now= System.currentTimeMillis();

      long[] rv = get_moons(now);
      new0 = rv[0];
      full = rv[1];
      new1 = rv[2];
      
      PhaseRV pr= phase(jtime(now));
      p = pr.phase;
      cphase = pr.pphase;
      aom = pr.mage;

      String str1 = String.format(
         "Moon:%3d%c  ", 
         (int)(Math.floor(cphase * 90 + 0.5)),
         (p < 0.5) ? '+' : '-') +
         daytime(aom);
      String str2 = " " + wrdate(new0);
      String str3 = " " + wrdate(full);
      String str4 = " " + wrdate(new1);
      return new String[] { str1, str2, str3, str4 };
   }
}

