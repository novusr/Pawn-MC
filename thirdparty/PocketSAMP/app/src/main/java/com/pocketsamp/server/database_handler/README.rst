Database client (``database_handler``)
=====================================

The JDBC side of PocketSAMP: it talks to the ``mariadbd`` the runtime service starts, lists
schemas and tables, runs a query and imports a ``.sql`` dump. The *engine* is in
``engine_handler/third_mariadb.kt``; this folder never starts or stops it.

It is documented because three things share the word "database" in this app — the engine,
this client, and the phpMyAdmin web view — and a bug report rarely says which one is meant.


File map
--------

``DatabaseManager.kt``
    Both the client and its result type. Every call is ``suspend`` and runs on
    ``Dispatchers.IO``; the connection is opened per call against the local engine
    (``127.0.0.1``, the port the user configured) rather than a remote host. Public
    surface: ``isServerAlive``, ``listDatabases``, ``createDatabase``, ``listTables``,
    ``executeQuery`` and ``importSql``.

``QueryResult`` (same file)
    The shape the database screen renders: column names plus rows as strings. Failed
    statements arrive as a ``QueryResult`` carrying the error text, not as an exception,
    because the screen shows them inline — which also means a caller cannot tell "empty
    result" from "the server is down" by the return value alone. ``isServerAlive`` is the
    check for that.


Rules that are easy to break
----------------------------

* **Never call these from the main thread.** They are ``suspend`` precisely so the caller
  is forced into a coroutine; a blocking JDBC call on the UI thread freezes the whole
  console screen.
* **The server must be alive before the client is used.** ``isServerAlive`` is the check
  the screen does first; calling ``executeQuery`` against a stopped engine produces a
  connection-refused text in the result pane, which looks like a SQL error.
* **Connections are short-lived.** Each call opens and closes one; a cached connection
  holds a thread on the engine and survives a restart of the server, which is the bug that
  makes the pane fail until the app is killed.
* **Import reads a stream, not a path.** The file comes from SAF and may not be a real
  file on disk.
