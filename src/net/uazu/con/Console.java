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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import net.uazu.con.tile.Tile;
import net.uazu.event.EventLoop;


/**
 * Console full-screen input/output handling (like curses).
 *
 * <p>Assumptions:</p> 
 * 
 * <ul>
 *
 * <li>Assumes an ANSI-compatible terminal, like the Linux console,
 * GNU screen or one of the X-based terminals.  Handles both 8-colour
 * and 256-colour terminals, and all Java-supported character
 * encodings, optimised for UTF-8 and ISO-8859-1.  Uses simple control
 * sequences that should work everywhere.</li>
 *
 * <li>Assumes single-width monospaced font with 1:1 mapping between
 * Unicode code points and displayed characters -- so there is no
 * handling for double-width fonts like Chinese/Japanese (yet) nor
 * fonts that can't work without combining characters.</li>
 *
 * <li>Assumes that multi-byte keypresses (e.g. Fn, arrows) arrive in
 * the same read packet.  This is true of most modern cases.  It
 * wouldn't be true of a slow serial line connection to a real ANSI
 * terminal.</li>
 *
 * </ul>
 *
 * <p>On doing the first call, the terminal is switched to the correct
 * mode, and a thread is setup to handle input keypresses.  These keep
 * on running until exit (when automatic cleanup occurs) or until the
 * {@link #pause} method is called which temporarily disables the
 * special terminal handling so that an external program can be run.
 * Any following call then reinitialises the terminal for full-screen
 * handling.</p>
 *
 * <h3>Internal character representation</h3>
 *
 * <p>The internal 'coloured-character' representation is a 32-bit int
 * containing the Unicode code-point (bottom 21 bits) and an
 * attribute-set index (top 11 bits).  VT100 graphics (e.g. for
 * line-drawing) are mapped to the above-Unicode code point range of
 * 1FFF00 to 1FFF7F.  Coloured-character codes are formed as
 * follows:</p>
 *
 * <ul>
 *
 * <li>Normal characters are formed from the attribute-set index 'hfb'
 * and the Unicode character 'ch' using {@link #gencc}.  The
 * components can be extracted using {@link #getch} and {@link
 * #gethfb}.</li>
 * 
 * <li>Box characters are formed using {@code Console.box[??]} as the
 * character argument to {@link #gencc}.  See {@link #box}.  To detect
 * box characters, the {@link #boxtyp} call may be used.</li>
 *
 * <li>Other special characters may be available in the VT100
 * code-page depending on the terminal.  See the constants in this
 * class starting with {@code vt100_} or {@code misc_}.  These are
 * formed with {@link #gencc} the same way as normal characters.</li>
 *
 * <li>Coloured characters may be inverted reversibly using {@link
 * #invert}.  The attribute-set indexes may be inverted similarly
 * using {@link #invhfb}.</li>
 *
 * </ul>
 * 
 * <h3>Attribute-set indexes</h3>
 *
 * <p>There are 2048 attribute-set indexes.  The first 128 are fixed
 * and are mapped to all the colour-combinations available on a basic
 * 8-colour ANSI terminal on VGA textmode-style hardware (like the
 * Linux console).  These take the form 0HFB in octal, where: 'H' is
 * highlight off/on (0/1), 'F' is foreground colour (0-7) and 'B' is
 * background colour (0-7).  Colours are in order of increasing
 * intensity.  Here they are in octal, with and without
 * highlighting:</p>
 * 
 * <p><pre>
 *  00 Black     04 Green       010 Grey           014 Bright green
 *  01 Blue      05 Cyan        011 Bright blue    015 Bright cyan
 *  02 Red       06 Brown       012 Bright red     016 Yellow     
 *  03 Magenta   07 White       013 Pink           017 Bright white
 * </pre></p>
 *
 * <p>(The reasoning behind this ordering is that it is easier to
 * remember.  Yellow is almost-white (6), blue is almost-black (1),
 * primaries are in increasing intensity order as blue (1), red (2),
 * green (4).  The rest are obvious combinations of these: magenta is
 * red and blue, cyan is green and blue, yellow is green and red.)</p>
 * 
 * <p>The attribute-set indexes from 128 onwards can be allocated to
 * select other combinations of colours and attributes, for example
 * underline or colours available on 256-colour consoles.  These
 * colour indexes must be allocated using {@link #allocateHFB}.  They
 * are allocated in pairs to make it easy to form the inverse.</p>
 *
 * <h3>Box characters</h3>
 * 
 * <p>Boxes are built up using the characters in the {@link #box}
 * array.  The indexes of the relevant characters are listed below.
 * Alternatively, {@link Area#box} can be used to draw a box, with
 * {@link Area#vstrut} and {@link Area#hstrut} for adding struts.
 * Another alternative is to draw a design using ASCII and then map it
 * to VT100 characters using {@link #ascii2box}.</p>
 *
 * <p><pre>
 * 10--12--14--12--6
 * |       |       |
 * 3       3       3
 * |       |       |
 * 11--12--15--12--7
 * |       |       |
 * 3       3       3
 * |       |       |
 * 9---12--13--12--5
 * </pre></p>
 *
 * <h3>Building applications on top of Console</h3>
 *
 * <p>Console may be used at several levels of complexity:</p>
 *
 * <ul>
 *
 * <li>Simple applications with straightforward update/refresh
 * requirements, for example a game.</li>
 *
 * <li>Tiled applications which delegate different areas (tiles) of
 * the page to different pieces of code, and which may have multiple
 * pages and more complex update requirements in general.</li>
 *
 * <li>Windowed applications with many different overlapping areas
 * which may be moved relative to one another or moved in front of or
 * behind one another.</li>
 *
 * </ul>
 *
 * <p>The first two are very simple to implement with the Console
 * classes -- see below.  Tiled applications are especially efficient
 * because all the tiles can write on the same backing Area because
 * there are no overlaps.  (Emacs is an example of a tiled
 * application, with three tiles: the editing area, the mode line and
 * the status line.)</p>
 *
 * <p>Windowed applications, however, are a lot more complex and there
 * has to be a lot more copying of data as each region must have its
 * own Area, and the Areas combined to form the final image to
 * display.  I did write some windowing classes, but they are complex
 * to understand and use.  In any case, tile-based applications are
 * often much better suited to the terminal.  Having to move or resize
 * windows is just a distraction, and you usually want things
 * full-screen and not limited to a subset of the available area.</p>
 *
 * <h3>Simplest possible application</h3>
 *
 * <p>For the simplest application, a single Area is allocated with
 * {@link #newArea}.  The application draws on this Area and calls
 * {@link #update} to write the changes to the screen whenever it
 * seems necessary.  The application installs an event handler using
 * {@code con.eloop.addHandler()} ({@link EventLoop#addHandler}), and
 * reacts to resize and key events.  On receiving a resize event, it
 * calls {@link #reinit} and then allocates a new Area with {@link
 * #newArea} and redraws and updates it.  That is sufficient for a
 * simple one-page application.</p>
 *
 * <p>For a simple multi-page application, the idea is the same,
 * except that several Areas are created, and whichever Area is
 * current is passed to {@link #update} to be shown on the screen.</p>
 *
 * <p>To increase efficiency by avoiding unnecessary calls to {@link
 * #update}, the simple application might choose to delay updates
 * until current processing is complete, hoping to gather several
 * updates into one.  This is done by registering the need for an
 * update with the {@link EventLoop} by calling {@link
 * EventLoop#reqUpdate}.  As soon as the current events have been
 * processed, the event loop issues an {@link
 * net.uazu.event.UpdateEvent}.  The application can catch this event
 * and make the {@link #update} call to update the screen.</p>
 * 
 * <h3>Tiled applications</h3>
 *
 * <p>A {@link net.uazu.con.tile.TiledApp} instance is created which
 * sets up an event handler to take care of resize, update, idle and
 * key events.  A separate {@link net.uazu.con.tile.Page} instance is
 * created in the TiledApp for each page of the application.  There is
 * always a current Page, which is the one shown.  Each Page has its
 * own backing Area.  Within the Page there may be a number of {@link
 * net.uazu.con.tile.Tile} instances which each take care of a
 * non-overlapping part of the area.  Tiles may embed subtiles if
 * necessary (e.g. a form with fields within it).</p>
 *
 * <p>Since each tile has sole ownership of its part of the top-level
 * Area (although sometimes its part may be clipped down to nothing
 * and be therefore invisible) it can update that area at any time.
 * So after the relayout phase, nothing needs to be redrawn unless it
 * actually needs changing.  The relayout phase takes care of
 * repositioning elements in the page, or scrolling elements within a
 * frame, or whatever.</p>
 * 
 * <p>The update cycle is handled automatically by the TiledApp, which
 * detects when any tile on the current page has made a change to that
 * Page's Area, and updates the screen from the area at the end of the
 * current event processing batch.</p>
 *
 * <p>Each Page may optionally nominate one Tile as the focus for
 * processing key events.  Key events are first passed to the
 * {@link net.uazu.con.tile.Tile#keyover} handlers: first in the
 * TiledApp, then the Page, then in each of the tiles all the way down
 * to the focus Tile, and then it is passed to the
 * {@link net.uazu.con.tile.Tile#key} handlers in all the same objects
 * in reverse, back up the chain again.  The event stops at the first
 * handler that consumes it.  For example, this allows global override
 * keys to be set (which override the focus tile), as well as global
 * default keys (for keypresses not handled by the focus tile).</p>
 *
 * <p>Pages have to be able to relayout and redraw their tiles on
 * demand, most often when the window size changes.  Tiles need to be
 * able to redraw if their size is changed due to a Page relayout.
 * Both of these cases are handled through the {@link Tile#relayout}
 * call.</p>
 */
public class Console {
   /**
    * Conversion from increasing-intensity colours to ANSI colours.
    * ANSI colours are:
    *
    * <p><pre>
    *  0 Black     4 Blue          8 Grey             C Bright blue
    *  1 Red       5 Magenta       9 Bright red       D Pink
    *  2 Green     6 Cyan          A Bright green     E Bright cyan
    *  3 Brown     7 White         B Yellow           F Bright white
    * </pre></p>
    *
    * <p>The 0HFB values use increasing-intensity colours.  Once
    * converted into the attrset[] form, they are ANSI colours.
    */
   private static int colconv[] = {
      0, 4, 1, 5, 2, 6, 3, 7,
      8, 12, 9, 13, 10, 14, 11, 15
   };

   /**
    * Base of VT100 character set (as Unicode code-point) to use in
    * coloured-characters.
    */
   public static final int VT100_BASE = 0x1FFF00;
   
   /**
    * Array of box characters; index based on {@code b0-b3 <=>
    * Up,Down,Left,Right}
    *
    *<p><pre> 
    *  lqwqk
    *  x x x
    *  tqnqu
    *  x x x
    *  mqvqj
    *</pre></p>
    */
   public static final int[] box = new int[] {
      ' ', ' ', ' ', 'x' | VT100_BASE,
      ' ', 'j' | VT100_BASE, 'k' | VT100_BASE, 'u' | VT100_BASE,
      ' ', 'm' | VT100_BASE, 'l' | VT100_BASE, 't' | VT100_BASE,
      'q' | VT100_BASE, 'v' | VT100_BASE, 'w' | VT100_BASE, 'n' | VT100_BASE
   };

   /**
    * Mapping from ASCII to box characters, using ASCII characters
    * that approximate the appearance of the corresponding box
    * characters.  This can be used to keep a line-drawing design in
    * ASCII and then convert it as it is drawn:
    *
    * <p><pre>
    * (r)--(-)--(v)--(-)--(i)
    *  |         |         |
    * (|)       (|)       (|)
    *  |         |         |
    * (&gt;)--(-)--(+)--(-)--(&lt;)
    *  |         |         |
    * (|)       (|)       (|)
    *  |         |         |
    * (`)--(-)--(^)--(-)--(')
    * </pre></p>
    */
   public static final int[] ascii2box = new int[128];

   static {
      ascii2box['-'] = box[12];
      ascii2box['|'] = box[3];
      ascii2box['+'] = box[15];
      ascii2box['r'] = box[10];
      ascii2box['i'] = box[6];
      ascii2box['`'] = box[9];
      ascii2box['\''] = box[5];
      ascii2box['v'] = box[14];
      ascii2box['^'] = box[13];
      ascii2box['>'] = box[11];
      ascii2box['<'] = box[7];
   }

   // Other VT100 characters
   static public final int vt100_block50 = VT100_BASE | 'a';   // checker board (50% stipple)
   static public final int vt100_degree = VT100_BASE | 'f';    // degree symbol
   static public final int vt100_diamond = VT100_BASE | '`';   // diamond
   static public final int vt100_pi = VT100_BASE | '{';        // greek pi
   static public final int vt100_ge = VT100_BASE | 'z';        // greater-than-or-equal-to
   static public final int vt100_le = VT100_BASE | 'y';        // less-than-or-equal-to
   static public final int vt100_ne = VT100_BASE | '|';        // not-equal
   static public final int vt100_plusminus = VT100_BASE | 'g'; // plus/minus
   static public final int vt100_scan1 = VT100_BASE | 'o';     // scan line 1
   static public final int vt100_scan3 = VT100_BASE | 'p';     // scan line 3
   static public final int vt100_scan7 = VT100_BASE | 'r';     // scan line 7
   static public final int vt100_scan9 = VT100_BASE | 's';     // scan line 9
   static public final int vt100_bullet = VT100_BASE | '~';    // bullet

   // Other characters that appear on the VT100 code-page on some terminals
   static public final int misc_arrowD = VT100_BASE | '.';       // arrow pointing down
   static public final int misc_arrowL = VT100_BASE | ',';       // arrow pointing left
   static public final int misc_arrowR = VT100_BASE | '+';       // arrow pointing right
   static public final int misc_arrowU = VT100_BASE | '-';       // arrow pointing up
   static public final int misc_block100 = VT100_BASE | '0';     // solid square block
   static public final int misc_lantern = VT100_BASE | 'i';      // lantern symbol
   static public final int misc_block25 = VT100_BASE | 'h';      // board of squares (25% stipple?)

   // Note that the init sequences force white-on-black because we
   // don't know if the terminal is maybe set to black-on-white.  We
   // need to know initial conditions to get our idea of the remote
   // display correct.
   static private final byte[] ansi_init_utf8 = {
      27, 'c',                // Full reset.  Also sets UTF-8 mode on Linux console
      27, '%', 'G',           // Set UTF-8 mode
      27, '[', '0', ';', '3', '7',
      ';', '4', '0', 'm',     // White on black
      27, '[', 'J',           // Erase whole display to white on black
      27, '[', '3', '4', 'l', // Block cursor
      27, '(', 'B',	      // G0 is Latin-1
      27, ')', '0',	      // G1 is VT100-graphics
   };
   
   static private final byte[] ansi_init_non_utf8 = {
      27, 'c',                // Full reset.  Also sets UTF-8 mode on Linux console
      27, '%', '@',           // Set non-UTF-8 mode
      27, '[', '0', ';', '3', '7',
      ';', '4', '0', 'm',     // White on black
      27, '[', 'J',           // Erase whole display to white on black
      27, '[', '3', '4', 'l', // Block cursor
      27, '(', 'B',	      // G0 is Latin-1
      27, ')', '0',	      // G1 is VT100-graphics
   };

   static private final byte[] ansi_underline_cursor = {
      27, '[', '3', '4', 'h'
   };

   static private final byte[] ansi_hide_cursor = {
      27, '[', '?', '2', '5', 'l',
      27, '[', '?', '1', 'c'
   };
               
   static private final byte[] ansi_show_cursor = {
      27, '[', '?', '2', '5', 'h',
      27, '[', '?', '0', 'c'
   };

   static private final byte[] ansi_to_origin = {
      27, '[', 'H'
   };

   static private final byte[] ansi_reset_attrs = {
      27, '[', '0', 'm'
   };

   static private final byte[] xterm_256_fg = {
      27, '[', '3', '8', ';', '5', ';'     // xterm 256-colour FG select
   };
   
   static private final byte[] xterm_256_bg = {
      27, '[', '4', '8', ';', '5', ';'     // xterm 256-colour BG select
   };

   static private final byte[] xterm_set_colour_1 = {
      27, ']', '4', ';'
   };
   static private final byte[] xterm_set_colour_2 = {
      ';', 'r', 'g', 'b', ':',
   };

   /**
    * Mask to extract the unicode character out of a
    * coloured-character.
    */
   public static final int CHAR_MASK = 0x001FFFFF;
   
   /**
    * Mask to extract the attribute-set index out of a
    * coloured-character.
    */
   public static final int ATTRSET_MASK = 0xFFE00000;

   /**
    * Shift to use with {@code >>>} to extract the attribute-set index
    * out of the coloured-character.
    */
   public static final int ATTRSET_SHIFT = 21;
   
   /**
    * Invert a coloured character, reversibly.  (Two inverts result in
    * original character.)
    */
   public static int invert(int cch) {
      if (cch < (128 << ATTRSET_SHIFT))
         return (cch&0xF81FFFFF) + ((cch&0x07000000) >> 3) + ((cch&0x00E00000) << 3);
      return cch ^ 1;
   }

   /**
    * Invert a HFB colour-spec.
    */
   public static int invhfb(int hfb) {
      if (hfb < 128)
         return (hfb & 0100) | ((hfb & 070) >> 3) | ((hfb & 007) << 3);
      return hfb ^ 1;
   }

   /**
    * Construct a coloured-character.
    * @param ch Unicode character (0 to 0x10FFFF)
    * @param hfb Attribute-set index, either
    * highlight-foreground-background code: 0HFB (0-127) or an
    * attribute-set index allocated with
    * {@link #allocateHFB(int,int,boolean,boolean)} or
    * {@link #allocateHFB(String)}
    */
   public static int gencc(int ch, int hfb) {
      return ch | hfb << ATTRSET_SHIFT;
   }

   /**
    * Get the Unicode character out of the given coloured-character.
    */
   public static int getch(int cch) {
      return cch & CHAR_MASK;
   }

   /**
    * Get the attribute-set index out of the given coloured-character.
    */
   public static int gethfb(int cch) {
      return cch >>> ATTRSET_SHIFT;
   }
   
   /**
    * Swap char: Remove the character from 'cch' and replace it with
    * 'ch'.
    */
   public static int swchar(int ch, int cch) {
      return (cch & ATTRSET_MASK) | ch;
   }

   /**
    * Return the box type (0bRLDU) of the given coloured character, or
    * 0 if not a box character.
    */
   public static int boxtyp(int cch) {
      cch &= CHAR_MASK;
      if (cch >= VT100_BASE)
         for (int a = 3; a<16; a++)
            if (box[a] == cch)
               return a;
      return 0;
   }
   
   //------------------------------------------------------------------------
   
   /**
    * Event loop associated with this Console.
    */
   public final EventLoop eloop;

   /**
    * Attribute-set for each of the attribute-set indexes.  b17 is
    * underlined, b16 is bold, b15-b8 is foreground colour index,
    * b7-b0 is background colour index.
    */
   private final int[] attrset;

   /**
    * Number of attribute sets allocated.
    */
   private int n_attrset;
   
   /**
    * Bold bit in attrset[] value
    */
   private final int ATTRSET_BOLD = 0x10000;

   /**
    * Underline bit in attrset[] value
    */
   private final int ATTRSET_UNDERLINE = 0x20000;
   
   /**
    * Allocated colours, in form 0xRRGGBB.  Only index 16 onwards are
    * used, so as not to overwrite the standard colours.
    */
   private final int[] colour;

   /**
    * Number of colours allocated.  Minimum is 16 (for the 16 basic
    * colours, which are left untouched).
    */
   private int n_colour;
   
   // Terminal interface.
   final ITerminal tif;

   // Private Area containing copy of data currently displayed on
   // remote screen; null forces re-init
   private Area disp = null;
   
   // Output buffer
   private byte[] outbuf = new byte[4096];
   private int ob_off = 0;

   // Cleanup string
   private byte[] cleanup = new byte[0];
   
   // Index of attribute-set currently active in the remote terminal
   private int d_hfb;
   
   // Remote terminal in alternate character set?
   private boolean d_altch;
   
   // Remote terminal showing cursor?
   private boolean d_curs;

   // Running KeyThread, or null.
   private KeyThread key_thread = null;

   // Debug output
   private FileWriter debug_out;

   // Is this a UTF-8 console?
   private final boolean isUTF8;
   
   // Is this an ISO-8859-1 console?
   private final boolean isLatin1;

   // For other character sets, these are use to handle the encoding
   private final CharsetEncoder encoder;
   private final CharBuffer encoder_in;
   private final ByteBuffer encoder_out;
   
   /**
    * Is this a 256-colour console?
    */
   public final boolean is256Color;
   
   /**
    * Construct a Console instance to manage output for a given
    * terminal.  Starts keyboard thread.
    */
   public Console(ITerminal tif, EventLoop eloop) {
      this.tif = tif;
      this.eloop = eloop;

      is256Color = tif.is256Color();

      // Preallocate first 128 slots using basic colours.  Uses bold
      // to get extra 8 colours on Linux-like terminals, or addresses
      // those colours directly on 256-colour consoles.
      if (is256Color) {
         attrset = new int[2048];
         for (int a= 0; a<128; a++)
            attrset[a] = 
               (colconv[(a&0170)>>3] << 8) |
               colconv[a&0007];
         colour = new int[256];
      } else {
         attrset = new int[256];  // Max required is 128 + 64 * 2 (ul, ul/bold)
         for (int a= 0; a<128; a++)
            attrset[a] = ((a&0100)<<10) |
               (colconv[(a&0070)>>3] << 8) |
               colconv[a&0007];
         colour = new int[16];
      }

      // We can't be sure how the basic colours are set up by the
      // user, so we don't use them to match colours being allocated.
      // If the user wants to use a basic colour they must specify it
      // directly (e.g. #0 for black in allocateHFB).
      for (int a = 0; a<16; a++)
         colour[a] = -1;
      reinitHFB();

      // Charset setup
      Charset cs = tif.getCharset();
      String csname = cs.name();
      isUTF8 = csname.equals("UTF-8");
      isLatin1 = csname.equals("ISO-8859-1");
      if (!isLatin1 && !isUTF8) {
         encoder = cs.newEncoder();
         encoder_in = CharBuffer.allocate(2);
         encoder_out = ByteBuffer.allocate(32);
         encoder.onMalformedInput(CodingErrorAction.IGNORE);
         encoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
      } else {
         encoder = null;
         encoder_in = null;
         encoder_out = null;
      }
      
      key_thread = new KeyThread(this, eloop);
      key_thread.start();
   }

   /**
    * Clear all the attribute-set and colour allocations, meaning that
    * allocations will start again from the beginning, reusing the
    * attribute-set indexes again.  Any code which previously
    * allocated an attribute-set index may find that its colours
    * change, so this should ideally be used by an application only if
    * it is able to refresh all of its stored attribute-set indexes
    * after this call.
    */
   public void reinitHFB() {
      n_colour = 16;
      n_attrset = 128;
   }

   /**
    * Allocate an attribute-set based on a string spec of the form
    * {@code "HFB"} or {@code "<FG>/<BG>[/<ATTR>]"}.  The HFB form is
    * 3 octal digits which give one of the basic-colour combinations
    * directly as documented above.  In the longer form, FG/BG can be
    * in the form of 3 or 6 hex digits, i.e. RGB or RRGGBB, or '#' and
    * one hex digit to select a basic ANSI colour by its index (0-F),
    * or '%' and one or two octal digits to select using the
    * colour-intensity scale (%0 black, %1 blue up to %7 white, then
    * %10 for grey up to %16 bright yellow and finally %17 bright
    * white).  Note: the 3-digit form has its digits repeated to get
    * the 6-digit form, e.g. 5F5 becomes 55FF55.  The optional /ATTR
    * part may be /U, /B, /UB or /BU to select underline and/or bold.
    * @return Valid attribute-set index, or -1 for invalid spec or if
    * the tables are full
    */
   public int allocateHFB(String spec) {
      int len = spec.length();
      int i1 = spec.indexOf('/');
      if (i1 < 0) {
         // HFB form
         try {
            int val = Integer.parseInt(spec, 8);
            if (len == 3 && val >= 0 && val <= 0177)
               return val;
         } catch (NumberFormatException e) {}  // Drop through
         return -1;
      }
      int i2 = spec.indexOf('/', i1+1);
      if (i2 < 0)
         i2 = len;
      String fg = spec.substring(0, i1);
      String bg = spec.substring(i1+1, i2);
      boolean bold = false;
      boolean underline = false;
      for (int a = i2+1; a<len; a++) {
         switch (spec.charAt(a)) {
         case 'B':
            bold = true;
            break;
         case 'U':
            underline = true;
            break;
         default:
            return -1;
         }
      }
      return allocateHFB(hex2rgb(fg), hex2rgb(bg), bold, underline);
   }

   /**
    * Convert hex RGB or RRGGBB into an integer 0xRRGGBB, or #N to
    * range -16 to -1.  Returns 0x7FFFFFFF if invalid.
    */
   private static int hex2rgb(String spec) {
      try {
         if (spec.startsWith("#")) {
            int val = Integer.parseInt(spec.substring(1), 16);
            if (val >= 0 && val < 16)
               return -16 + val;
         } else if (spec.startsWith("%")) {
            int val = Integer.parseInt(spec.substring(1), 8);
            if (val >= 0 && val < 16)
               return -16 + colconv[val];
         } else {
            int rgb = Integer.parseInt(spec, 16);
            switch (spec.length()) {
            case 3:
               return (((rgb & 0xF00) << 8) |
                       ((rgb & 0x0F0) << 4) |
                       ((rgb & 0x00F))) * 0x11;
            case 6:
               return rgb;
            }
         }
      } catch (NumberFormatException e) {}   // Drop through
      return 0x7FFFFFFF;
   }
   
   /**
    * Allocate an attribute-set index for the given combination of
    * colours and attributes.  Reuses existing slots if possible.
    * Colours are 24-bit: 0xRRGGBB, range 00 to FF on each component,
    * or alternatively (colour-index - 16) to use a basic colour
    * directly (range -16 to -1 becomes 0 to 15).  Returns -1 if no
    * space left in tables, or if an invalid colour is passed.  Note
    * that HFB is used as a short-hand for the attribute-set index,
    * even though the value is no longer in the form 0HFB when
    * allocated with this method.
    * @return Valid attribute-set index or -1 if colours are invalid
    * or if tables are full
    */
   public int allocateHFB(int fg, int bg, boolean bold, boolean underline) {
      fg = allocateRGB(fg);
      bg = allocateRGB(bg);
      if (fg < 0 || bg < 0 || 
          !is256Color && (fg >= 8 || bg >= 8))
         return -1;
      int val = (fg << 8) | bg |
         (bold ? ATTRSET_BOLD : 0) |
         (underline ? ATTRSET_UNDERLINE : 0);
      for (int a = 128; a<n_attrset; a++) {
         int set = attrset[a];
         if (set == val)
            return a;
      }
      if (n_attrset < attrset.length) {
         int rv = n_attrset;
         attrset[n_attrset++] = val;
         attrset[n_attrset++] =
            (val & 0xFFFF0000) | ((val & 0xFF00) >> 8) | ((val & 0x00FF) << 8);
         return rv;
      }
      return -1;
   }

   /**
    * Allocate one of the 256 indexes available on the terminal to
    * hold the given RGB colour.  Arguments -16 to -1 return basic
    * colour slots 0 to 15 directly.  Otherwise reuses an existing
    * slot with the same colour if possible (although basic colours
    * are never returned as we don't know how the user's terminal
    * might be configured).  Returns -1 if none are free or if the
    * colour is invalid.
    */
   private int allocateRGB(int rgb) {
      if (rgb >= -16 && rgb < 0)
         return rgb + 16;
      if (rgb < 0 || rgb > 0xFFFFFF)
         return -1;
      for (int a = 16; a<n_colour; a++) 
         if (colour[a] == rgb)
            return a;
      if (n_colour < colour.length) {
         int ii = n_colour;
         colour[n_colour++] = rgb;
         putSetColour(ii, rgb);
         return ii;
      }
      return -1;
   }

   /**
    * Clear the terminal screen, forcing a full update next update
    * instead of an incremental update.  Starts the keyboard handler
    * as well if it is not already running.
    */
   public void clear() {
      setup_cleanup(true);

      // Unfortunately we have to send UTF-8 mode on/off sequences
      // because ESC c on Linux Console sets UTF-8 mode.
      put(isUTF8 ? ansi_init_utf8 : ansi_init_non_utf8);
      for (int a = 16; a<n_colour; a++)
         putSetColour(a, colour[a]);
      flush();

      d_hfb = 070;     // White-on-black is set up by init string
      if (disp == null || disp.rows != tif.getSY() || disp.cols != tif.getSX()) {
         disp = new Area(tif.getSY(), tif.getSX(), d_hfb);
      } else {
         disp.clr(gencc(' ', d_hfb));
      }
      d_altch = false;
      d_curs = true;

      key_thread.rawMode(true);
   }

   /**
    * Get the width of the screen.
    */
   public int getSX() {
      if (disp == null) clear();
      return disp.cols;
   }

   /**
    * Get the height of the screen.
    */
   public int getSY() {
      if (disp == null) clear();
      return disp.rows;
   }

   /**
    * Create a new Area with the same size as the screen, cleared to
    * white on black.
    */
   public Area newArea() {
      if (disp == null) clear();
      return new Area(disp.rows, disp.cols, 0070);
   }
   
   /**
    * Create a new Area with the same size as the screen, cleared to
    * the given HFB.
    */
   public Area newArea(int hfb) {
      if (disp == null) clear();
      return new Area(disp.rows, disp.cols, hfb);
   }
   
   /**
    * Send a beep to the output.
    */
   public void beep() {
      put(7); flush();
   }

   /**
    * Force complete reinitialization of the screen, clearing it and
    * checking the terminal size.  This should be used whenever the
    * ResizeEvent is caught, and also if the user presses ^L.
    */
   public void reinit() {
      disp = null;
      clear();
   }

   /**
    * Release control of the TTY and revert to standard TTY settings,
    * colours, etc.  Control can be reestablished by executing
    * reinit() or update().  This may be used before executing an
    * external tool for user interaction.  However, this call is not
    * required before exiting the program, as that is handled
    * automatically by atexit() hooks.
    */
   public void pause() {
      disp = null;
      tif.output(cleanup, cleanup.length);
      setup_cleanup(false);

      if (key_thread != null)
         key_thread.rawMode(false);
   }

   /**
    * Update the terminal screen to match the given area's contents.
    * The area given should be the same size as the screen.  If it
    * isn't, then a best-effort to display it is made, and 'true' is
    * returned.
    */
   public boolean update(Area area) {
      if (disp == null)
         clear();

      boolean size_change = false;
      if (area.rows != disp.rows || area.cols != disp.cols) {
         Area new_area = new Area(disp.rows, disp.cols);
         new_area.put(area, 0, 0);
         area = new_area;
         size_change = true;
      }

      update_aux(area);
      return size_change;
   }

   // Check there is space for the given number of output bytes.  If
   // not, expand the buffer.
   private final void mksp(int len) {
      if (ob_off + len > outbuf.length) {
         int newlen = outbuf.length * 2;
         while (newlen < ob_off + len)
            newlen *= 2;
         byte[] tmp = new byte[newlen];
         System.arraycopy(outbuf, 0, tmp, 0, ob_off);
         outbuf = tmp;
      }
   }

   // Write byte to output buffer
   private final void put(int ch) {
      mksp(1);
      outbuf[ob_off++] = (byte) ch;
   }

   // Write 2 bytes to output buffer
   private final void put(int ch, int ch2) {
      mksp(2);
      outbuf[ob_off++] = (byte) ch;
      outbuf[ob_off++] = (byte) ch2;
   }

   // Write 3 bytes to output buffer
   private final void put(int ch, int ch2, int ch3) {
      mksp(3);
      outbuf[ob_off++] = (byte) ch;
      outbuf[ob_off++] = (byte) ch2;
      outbuf[ob_off++] = (byte) ch3;
   }

   // Write bytes to output buffer
   private final void put(byte[] data) {
      int len = data.length;
      mksp(len);
      for (int a = 0; a<len; a++)
         outbuf[ob_off++] = data[a];
   }

   // Write decimal number (max 3 digits) to output buffer
   private final void put3dig(int val) {
      if (val >= 100) {
         put('0' + val/100, '0' + (val/10)%10, '0' + val%10);
      } else if (val >= 10) {
         put('0' + val/10, '0' + val%10);
      } else {
         put('0' + val);
      }
   }

   // Write 2-digit hex number
   private final void put2hex(int val) {
      put("0123456789ABCDEF".charAt((val >> 4) & 15),
          "0123456789ABCDEF".charAt(val & 15));
   }

   // Send SI or SO to switch character sets
   private final void putAltSelect(boolean alt) {
      put(alt ? 14 : 15);
   }

   // Move to coordinate
   private final void putMoveTo(int y, int x) {
      put(27, '[');
      put3dig(1 + y);
      put(';');
      put3dig(1 + x);
      put('H');
   }
   
   // Send colour-change codes to change from hfb0 to hfb1.  If there
   // is no clear previous state, hfb0 should be -1.
   private final void putAttrSet(int hfb0, int hfb1) {
      // Use error colours in case of invalid HFB
      if (hfb1 < 0 || hfb1 >= attrset.length)
         hfb1 = 0162;
      if (hfb0 >= attrset.length)
         hfb0 = 0162;
      
      int val1 = attrset[hfb1];
      int bold1 = val1 & ATTRSET_BOLD;
      int ul1 = val1 & ATTRSET_UNDERLINE;
      int fg1 = (val1 >> 8) & 255;
      int bg1 = val1 & 255;
      
      boolean hasold = hfb0 >= 0 && hfb0 < attrset.length;
      int val0 = hasold ? attrset[hfb0] : -1;
      int bold0 = hasold ? (val0 & ATTRSET_BOLD) : 0;
      int ul0 = hasold ? (val0 & ATTRSET_UNDERLINE) : 0;
      int fg0 = hasold ? ((val0 >> 8) & 255) : -1;
      int bg0 = hasold ? (val0 & 255) : -1;
      
      // Do bold/underline and colours 0-7 with one sequence and
      // colours 8-255 with separate sequences
      mksp(20);
      int start_seq = ob_off;
      put(27, '[');

      if (!hasold)
         put('0', ';');
      if (0 != bold1 && 0 == bold0)
         put('1', ';');        // Bold on
      if (0 == bold1 && 0 != bold0)
         put('2', '2', ';');   // Bold off
      if (0 != ul1 && 0 == ul0)
         put('4', ';');        // Underline on
      if (0 == ul1 && 0 != ul0)
         put('2', '4', ';');   // Underline off
      if (fg1 != fg0 && fg1 < 8)
         put('3', '0' + fg1, ';');
      if (bg1 != bg0 && bg1 < 8)
         put('4', '0' + bg1, ';');

      if (ob_off == start_seq + 2) {
         // Rewind
         ob_off = start_seq;
      } else {
         outbuf[ob_off-1] = 'm';
      }

      // Handle colours >= 8
      if (fg1 != fg0 && fg1 >= 8) {
         put(xterm_256_fg);
         put3dig(fg1);
         put('m');
      }
      if (bg1 != bg0 && bg1 >= 8) {
         put(xterm_256_bg);
         put3dig(bg1);
         put('m');
      }
   }      

   /**
    * Set a colour on a 256-colour console
    */
   private final void putSetColour(int ii, int rgb) {
      put(xterm_set_colour_1);
      put3dig(ii);
      put(xterm_set_colour_2);
      put2hex(rgb >> 16);
      put('/');
      put2hex(rgb >> 8);
      put('/');
      put2hex(rgb);
      put(27, '\\');
   }
   
   /**
    * Write out a unicode character according to the current charset.
    * Handles UTF-8 and Latin1 inline.  Other encodings go via the
    * Java CharsetEncoder.  If the character can't be output then a
    * '?' is written instead.  VT100 characters are also handled here.
    * NOTE: REMEMBERS SI/SO STATE!  If this method is used and the
    * data is never output then remote terminal may be left in wrong
    * character set.
    */
   private final void putUnicode(int ch) {
      if (ch >= VT100_BASE + 32 && ch < VT100_BASE + 127) {
         if (!d_altch)
            putAltSelect(d_altch = true);
         put(ch - VT100_BASE);
         return;
      }
      if (d_altch)
         putAltSelect(d_altch = false);
      
      if (ch < 32 || ch >= 127 && ch < 160) {
         put('?');
         return;
      }
      if (isLatin1) {
         put(ch < 256 ? ch : '?');
         return;
      } 
      if (isUTF8) {
         mksp(4);
         if (ch < 0x80) {
            outbuf[ob_off++] = (byte) (ch & 0x7F);
         } else if (ch < 0x800) {
            outbuf[ob_off++] = (byte) (0xC0 | (ch >> 6));
            outbuf[ob_off++] = (byte) (0x80 | (ch & 0x3F));
         } else if (ch < 0x10000) {
            outbuf[ob_off++] = (byte) (0xE0 | (ch >> 12));
            outbuf[ob_off++] = (byte) (0x80 | ((ch >> 6) & 0x3F));
            outbuf[ob_off++] = (byte) (0x80 | (ch & 0x3F));
         } else if (ch < 0x00110000) {
            outbuf[ob_off++] = (byte) (0xF0 | (ch >> 18));
            outbuf[ob_off++] = (byte) (0x80 | ((ch >> 12) & 0x3F));
            outbuf[ob_off++] = (byte) (0x80 | ((ch >> 6) & 0x3F));
            outbuf[ob_off++] = (byte) (0x80 | (ch & 0x3F));
         } else {
            outbuf[ob_off++] = '?';
         }
         return;
      }

      // Use CharsetEncoder
      encoder.reset();
      encoder_in.clear();
      if (ch < 0x10000) {
         encoder_in.put((char) ch);
      } else {
         ch -= 0x10000;
         encoder_in.put((char) (0xD800 | (ch >> 10)));
         encoder_in.put((char) (0xDC00 | (ch & 0x3FF)));
      }
      encoder_in.flip();
      encoder_out.clear();
      encoder.encode(encoder_in, encoder_out, true);
      encoder_out.flip();
      if (encoder_out.hasRemaining()) {
         while (encoder_out.hasRemaining())
            put(encoder_out.get());
      } else {
         put('?');
      }
   }

   // Write output buffer to stdout
   private final void flush() {
      tif.output(outbuf, ob_off);
      ob_off = 0;
   }

   /**
    * Update screen to match given area which is the same size.  This
    * is not optimised for cases where clearing areas or clearing the
    * whole screen could help, nor for cases where inserting/deleting
    * characters or lines would help.  However, it does make an effort
    * to minimise output in all other cases.  TODO: Do more
    * optimisation.
    */
   private final void update_aux(Area cur) {
      int sx= disp.cols;
      int sy= disp.rows;
      int sxy= sx*sy;
      int off, off0;

      if (d_curs)
         put(ansi_hide_cursor);

      put(ansi_to_origin);
      off0 = off = 0;

      while (true) {
         int hfb, cch, cnt;

         off0 = off;
         while (off < sxy && cur.arr[off] == disp.arr[off]) off++;
         if (off == sxy) break;

         // Change to new attributes
         cch = cur.arr[off];
         hfb = cch >>> ATTRSET_SHIFT;
         if (hfb != d_hfb) {
            putAttrSet(d_hfb, hfb);
            d_hfb = hfb;
         }
         
         // Get from previous position to here
         cnt= off - off0;
         if (cnt > 0) {
            // Try and perform some short skips by rewriting existing
            // unchanged characters (shorter than an escape sequence)
            if (cnt <= 5) {
               int o;
               for (o = off0; o < off; o++) {
                  if (cur.arr[o] >>> ATTRSET_SHIFT != hfb)
                     break;
               }
               if (o == off) {
                  for (o = off0; o < off; o++)
                     putUnicode(cur.arr[o] & CHAR_MASK);
                  cnt = 0;
               }
            }

            if (cnt > 0)
               putMoveTo(off/sx, off%sx);
         }

         // Write the character and any following characters that have
         // the same attributes
         cch = cur.arr[off];
         disp.arr[off] = cch;
         putUnicode(cch & CHAR_MASK);
         off++;
         while (off < sxy) {
            cch = cur.arr[off];
            if ((cch >>> ATTRSET_SHIFT) != hfb ||
                disp.arr[off] == cch)
               break;
            disp.arr[off] = cch;
            putUnicode(cch & CHAR_MASK);
            off++;
         }
      }

      // Re-enable cursor if required
      d_curs = false;
      if (cur.curs_x >= 0 && cur.curs_y >= 0) {
         putMoveTo(cur.curs_y, cur.curs_x);
         put(ansi_show_cursor);
         d_curs = true;
      }

      // Try and write it all out in one block.  If the terminal
      // handles it as one block, then there will be no disruption to
      // the user's display.
      flush();
   }

   /**
    * Set up the cleanup string, which puts the terminal in a sensible
    * state for the user, cursor on, sensible colours, etc.  This is
    * issued automatically by the front-end if the tool dies
    * unexpectedly.
    */
   private final void setup_cleanup(boolean dirty) {
      if (dirty) {
         // Underline cursor, cursor on
         put(ansi_underline_cursor);
         put(ansi_show_cursor);
         // Default character set
         putAltSelect(false);
         // Default colours
         put(ansi_reset_attrs);
         // Bottom line
         putMoveTo(tif.getSY()-1, 0);
         // Scroll up one
         put(10);
      }

      cleanup = new byte[ob_off];
      System.arraycopy(outbuf, 0, cleanup, 0, ob_off);
      ob_off = 0;

      tif.setCleanup(cleanup, cleanup.length);
   }

   /**
    * Dump out debugging message to a log: ./debug-log.
    */
   public void debug(String fmt, Object... args) {
      try {
         if (debug_out == null)
            debug_out = new FileWriter("./debug-log");
         
         debug_out.write(String.format(fmt, args) + "\n");
         debug_out.flush();
      } catch (IOException e) {
         throw new RuntimeException("Can't open log file: " + e.getMessage());
      }
   }
}

// END //
