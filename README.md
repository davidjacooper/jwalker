# JDirScanner

A simple Java library to recurse through a directory tree, including the contents of .zip and other
archive files.


## Basic Usage

```java
import au.djac.jdirscanner.JDirScanner;

var scanner = new JDirScanner();
scanner.forEachFile(
    Path.of("/path/to/scan/"),
    (Path displayPath, InputSupplier input, Map<String,String> fileMetadata) ->
    {
        InputStream is = input.get();
        // ...
    });
```


## Inclusions and Exclusions

```java
import au.djac.jdirscanner.JDirScanner;
import au.djac.jdirscanner.JDirScannerOptions;

var opts = new JDirScannerOptions();

```
