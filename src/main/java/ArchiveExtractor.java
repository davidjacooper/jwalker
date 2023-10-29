package au.djac.jdirscanner;

import java.nio.file.Path;
import java.util.*;

public abstract class ArchiveExtractor
{
    // The different archive formats seem to agree on '/' as a directory separator:
    //
    // * From the ZIP spec (https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT),
    //   Section 4.4.17.1:
    //     "The path stored MUST NOT contain a drive or device letter, or a leading slash. All
    //     slashes MUST be forward slashes '/' as opposed to backwards slashes '\' for
    //     compatibility with Amiga and UNIX file systems etc.  If input came from standard input,
    //     there is no file name field."
    //
    // * From the TAR spec (https://www.gnu.org/software/tar/manual/html_node/Standard.html):
    //     "The name field is the file name of the file, with directory names (if any) preceding
    //     the file name, separated by slashes."
    //
    // * From the 7Z spec (https://py7zr.readthedocs.io/en/latest/archive_format.html):
    //     "Path separator SHALL be normalized as ‘/’, which is as POSIX standard. FileName SHOULD
    //     be relative path notation."
    //
    // * The ARJ spec (https://github.com/FarGroup/FarManager/blob/master/plugins/multiarc/arc.doc/arj.txt)
    //   contains a flag indicating whether 'filename translated ("\" changed to "/")', which I
    //   think implies that "/" is used internally. (Not entirely unambiguous, admittedly.)
    //
    // I haven't located the RAR spec, if there even is one, but we can let the external 'unrar'
    // tool worry about directory separators anyway.
    //
    // The other formats supported by Commons Compress -- 'ar', 'cpio' and 'dump' -- are fairly
    // unimportant, though they are all UNIX-based.

    protected static final String ARCHIVE_DIRECTORY_SEPARATOR = "/";

    public abstract Set<String> getFileExtensions();
    public abstract void extract(JDirScanner dirScanner,
                                 String extension,
                                 Path fsPath,
                                 Path displayPath,
                                 InputSupplier input,
                                 Map<String,String> archiveMetadata) throws ArchiveSkipException;
}
