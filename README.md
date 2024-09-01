# JWalker

A simple Java library for recursively finding files, similar to [`Files.walkFileTree()`][Files.walkFileTree], though with archive support and with a somewhat different interface.

JWalker supports recursing into certain archive formats: those supported by [Apache Commons Compress][]---AR, ARJ, CPIO, DUMP, TAR, 7Z and ZIP---as well as RAR _if_ an external `unrar` command is available. Archive files may be arbitrarily nested.

Basic usage is as follows:

```java
import au.djac.jwalker.JWalker;
...

var jwalker = new JWalker();
jwalker.walk(
    Path.of("/path/to/scan/"),
    (path, input, fileAttr) ->
    {
        InputStream is = input.get();
        // ...
    });
```

The `walk()` method takes a callback, to be invoked once per file found. The callback accepts three parameters:

* `path` (a `Path` object) is the path name of the file.

    It should be used for labelling purposes only, not for actual file access. JWalker treats `.zip` files, etc. as if they were directories, resulting in paths like "`archive.zip/myfile.txt`", and such paths won't make sense to standard JDK I/O facilities.

* `input` can be used to retrieve the actual file contents, if needed. Calling `input.get()` returns an `InputStream`. However:

    * This is valid only for the duration of the callback (since archive files do not generally permit random access). If file content is required later on, you must read the `InputStream` _during_ the callback and store the data.

    * The `InputStream` must _not_ be closed by the client code (as it may need to be reused internally to obtain subsequent files).

* `fileAttr` (of type `au.djac.jwalker.attr.FileAttributes`) provides file metadata through a _map-like_ iterface. Different types of metadata will be available depending on the filesystem or archive the file is stored within.

    (This doesn't include metadata stored as part of the file content.)


<!--## Inclusions and Exclusions

```java
import au.djac.jwalker.JWalker;
import au.djac.jwalker.JWalker;

var opts = new JDirScannerOptions();

```-->

[Apache Commons Compress]: https://commons.apache.org/proper/commons-compress/index.html
[Files.walkFileTree]: https://docs.oracle.com/en%2Fjava%2Fjavase%2F11%2Fdocs%2Fapi%2F%2F/java.base/java/nio/file/Files.html#walkFileTree(java.nio.file.Path,java.nio.file.FileVisitor)
