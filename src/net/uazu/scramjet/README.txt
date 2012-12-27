==== Guide to the classes

Scramjet is the main entry point.  Once setup is done, it sleeps on
the main thread to handle idle shutdown.

SJProxy is a Thread that waits on a FIFO (#-in) and handles running a
tool and setting up input/output over the FIFOs.  There is one SJProxy
instance per proxy connection.

SJInputStream and SJOutputStream map between Java in/out streams and
the messages sent over the FIFO connection.

MsgReader and MsgWriter handle reading/writing/formatting/parsing the
messages sent over the FIFO connections.

Tool: all tools should inherit from Tool.  The environment of the tool
is available as instance variables.  There are convenience methods for
printing to stdout and beeping, etc.

"nailgun/" contains nailgun-derived code to redirect use of
System.in/out/err and System.exit() to the tool's own handlers.  It is
not strictly necessary to use this, but it makes it more convenient
when adapting existing code.

"con/" contains full-screen console app-related code.  ConsoleTool
provides the framework for a console app, loading up the ConsoleMod
and starting the event loop.  There are example tools under
"con/tool/".

"eclipse/" contains Eclipse plugin related code.  There are example
tools under "eclipse/tool/".

"con/" and "eclipse/" are separated off so that they are easier to
exclude to reduce dependencies.
