Scramjet for UNIX: Java acceleration for terminal apps
======================================================

This fully usable but still a work-in-progress.  I am adding new features and reorganizing old ones as I need them, according to how much free time I get to work on it.  --Jim

### Main applications

* __UNIX command-line tools:__ Scramjet allows fast UNIX-style command-line tools to be written in Java by avoiding the JVM startup time.
* __UNIX terminal applications:__ Scramjet allows writing efficient ncurses-style fullscreen Unicode ANSI text applications (editors, mail agents, viewers) in Java.
* __Eclipse hooks:__ Scramjet allows monolithic Java applications such as Eclipse to be controlled from the UNIX command-line, running commands on demand within the Eclipse JVM, with input/output via the terminal.

### License

Released under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0

### Website

See http://uazu.net/scramjet/ for more information, screenshots, examples, etc.

### Quick-Start and Example Usage

```bash
# Build everything
cd src
./mk

# Start JVM (also creates ~/.scramjet the first time)
out/scramjet -S

# Kill JVM
out/scramjet -K

# Start JVM (automatically) and show test page.  NOTE: you might need
# to press ^L (redraw) to display line-characters correctly on some
# GNOME terminals -- this seems to be a libVTE bug.
out/scramjet net.uazu.scramjet.test.ConsoleTest0
^C

# If something was not working on the test page, adjust LANG and TERM
# environment variables as required and re-run as necessary.  We need
# -R option here to restart the JVM to get it to pick up LANG changes.
export LANG=...
export TERM=...
out/scramjet -R net.uazu.scramjet.test.ConsoleTest0
^C

# Start a game.  (NOTE: both dots and slashes are okay for package
# separators)
out/scramjet net/uazu/scramjet/test/Teeclub

# Whilst it is running, on another terminal show the stacktraces of
# all the threads running in the JVM.
out/sj-threads -l
```
