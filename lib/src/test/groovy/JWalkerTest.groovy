package au.djac.jwalker
import au.djac.jwalker.attr.*
import static au.djac.jwalker.attr.FileType.*

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import java.nio.charset.*
import java.nio.file.*

import spock.lang.*

class JWalkerTest extends Specification
{
    private Path tempDir

    def setup()
    {
        tempDir = Files.createTempDirectory("jwalkertest")
    }

    private void createFiles(Path dir, Map<String,Object> testStructure)
    {
        testStructure.each {
            name, children ->
            def entry = dir.resolve(name)
            if(children == 1)
            {
                Files.writeString(entry, name)
            }
            else
            {
                Files.createDirectory(entry)
                createFiles(entry, children) // Recurse!!
            }
        }
    }

    def cleanup()
    {
        FileUtils.deleteDirectory(tempDir.toFile()) // Commons-IO recursive directory deletion.
    }

    def 'single file'()
    {
        given:
            def file = tempDir.resolve("testfile")
            def content = "test data"
            Files.writeString(file, content)

        when:
            def reportedPath
            def reportedContent
            def reportedAttr

            new JWalker().walk(file) {
                path, input, attr ->
                reportedPath = path
                reportedContent = IOUtils.toString(input.get(), StandardCharsets.UTF_8)
                reportedAttr = attr
            }

        then:
            reportedPath == file
            reportedContent == content
            reportedAttr.get(FileAttributes.SIZE) == content.length()
    }

    def 'paths, includes and excludes'()
    {
        given:
            createFiles(tempDir, [
                "1.j": 1,
                "2.j": 1,
                "3.py": 1,
                "4.py": 1,
                "d1": [
                    "5.j": 1,
                    "6.py": 1,
                    "d2": [
                        "7.j": 1,
                    ]
                ],
                "d3": [
                    "8.j": 1,
                    "d4": [
                        "9.j": 1,
                    ]
                ],
                "10.j": 1,
                "11.py": 1,
                "d5": [
                    "12.j": 1,
                ],
            ])

        when:
            def walker = new JWalker()
            inclusions.forEach { walker.include(it) }
            exclusions.forEach { walker.exclude(it) }

            def actualFiles = []

            walker.walk(tempDir) {
                displayPath, input, attr ->
                actualFiles.add(tempDir.relativize(displayPath).toString())
            }

        then:
            expectedFiles.sort(false) == actualFiles.sort(false)

        where:
            inclusions     | exclusions      || expectedFiles
            // No includes/excludes
            []             | []              || ["1.j","2.j","3.py","4.py","d1/5.j","d1/6.py","d1/d2/7.j","d3/8.j","d3/d4/9.j","10.j","11.py","d5/12.j"]

            // Includes
            ["*.j"]        | []              || ["1.j","2.j","d1/5.j","d1/d2/7.j","d3/8.j","d3/d4/9.j","10.j","d5/12.j"]
            ["*.py"]       | []              || ["3.py","4.py","d1/6.py","11.py"]
            ["*.py", "1*"] | []              || ["1.j","3.py","4.py","d1/6.py", "10.j","11.py","d5/12.j"]

            // Excludes
            []             | ["*.j"]         || ["3.py","4.py","d1/6.py","11.py"]
            []             | ["*.py" , "1*"] || ["2.j","d1/5.j","d1/d2/7.j","d3/8.j","d3/d4/9.j"]

            // Interactions between includes and excludes
            ["*.j"]        | ["*.j"]         || []
            ["1*"]         | ["*.py"]        || ["1.j", "10.j","d5/12.j"]

            // Excluding subdirectories
            []             | ["d1"]          || ["1.j","2.j","3.py","4.py","d3/8.j","d3/d4/9.j","10.j","11.py","d5/12.j"]
            []             | ["d2"]          || ["1.j","2.j","3.py","4.py","d1/5.j","d1/6.py","d3/8.j","d3/d4/9.j","10.j","11.py","d5/12.j"]
            []             | ["d*"]          || ["1.j","2.j","3.py","4.py","10.j","11.py"]
    }


    private String PYTHON_SRC = """
        #!/usr/bin/python
        print("Hello world")
    """.stripIndent().trim()

    private String JAVA_SRC = """
        package hello;
        public class Hello
        {
            public static void main(String[] args)
            {
                System.out.println("Hello world");
            }
        }
    """.stripIndent().trim()

    private String C_SRC = """
        #include <stdio.h>

        int main(void)
        {
            printf("Hello world\\n");
            return 0;
        }
    """.stripIndent().trim()

    private String CSHARP_SRC = """
        using System;

        namespace Hello
        {
            class Hello
            {
                static void Main(string[] args)
                {
                    Console.WriteLine("Hello World");
                }
            }
        }
    """.stripIndent().trim()


    def 'content and archives'()
    {
        given:
            def archives = [
                'test-content.7z',
                'test-content.arj',    // Only uncompressed content supported
//                 'test-content-dump.dump', // TODO
                'test-content.cpio',
                'test-content.rar',
                'test-content.tar',
                'test-content.tar.br',
                'test-content.tar.bz2',
                'test-content.tar.gz',
//                 'test-content.tar.lz',
                'test-content.tar.lzma',
//                 'test-content.tar.lzo',
                'test-content.tar.sz',
                'test-content.tar.xz',
                'test-content.tar.Z',
                'test-content.tar.zst',
                'test-content.zip'
            ]

            def archiveCopies = [
                ('test-content.tb2'):  'test-content.tar.bz2',
                ('test-content.tbz'):  'test-content.tar.bz2',
                ('test-content.tbz2'): 'test-content.tar.bz2',
                ('test-content.tz2'):  'test-content.tar.bz2',
                ('test-content.taz'):  'test-content.tar.gz',
                ('test-content.tgz'):  'test-content.tar.gz',
                ('test-content.tlz'):  'test-content.tar.lzma',
                ('test-content.txz'):  'test-content.tar.xz',
                ('test-content.tZ'):   'test-content.tar.Z',
                ('test-content.taZ'):  'test-content.tar.Z',
                ('test-content.tzst'): 'test-content.tar.zst',
            ]

        when:
            // Copy archive file to the test directory
            for(name in archives)
            {
                println("TEST: content and archives: name=$name")
                FileUtils.copyInputStreamToFile(
                    getClass().getResourceAsStream(name),
                    tempDir.resolve(name).toFile()
                )
            }

            for(entry in archiveCopies)
            {
                FileUtils.copyInputStreamToFile(
                    getClass().getResourceAsStream(entry.value),
                    tempDir.resolve(entry.key).toFile()
                )
            }

            def files = [:]
            new JWalker().walk(tempDir) {
                displayPath, input, attr ->

                files[tempDir.relativize(displayPath).toString()] = IOUtils.toString(input.get(), StandardCharsets.UTF_8)
            }

            println(files.keySet())

            def actualPy     = { files["$it/hello.py"].trim() }
            def actualJava   = { files["$it/dir/Hello.java"].trim() }
            def actualC      = { files["$it/dir/hello.c"].trim() }
            def actualCSharp = { files["$it/dir/nested_dir/Hello.cs"].trim() }

        then:
            for(name in archives)
            {
                assert PYTHON_SRC == actualPy(name)
                assert JAVA_SRC   == actualJava(name)
                assert C_SRC      == actualC(name)
                assert CSHARP_SRC == actualCSharp(name)
            }
    }

    def 'content and archives: ar'()
    {
        // The 'ar' format gets its own special test, because it cannot (reliably, at any rate)
        // store directory paths.

        when:
            def archiveName = 'test-content.a'
            FileUtils.copyInputStreamToFile(
                getClass().getResourceAsStream(archiveName),
                tempDir.resolve(archiveName).toFile()
            )

            def files = [:]
            new JWalker().walk(tempDir) {
                displayPath, input, attr ->
                files[tempDir.relativize(displayPath).toString()] = IOUtils.toString(input.get(), StandardCharsets.UTF_8)
            }

        then:
            PYTHON_SRC == files["$archiveName/hello.py"].trim()
            JAVA_SRC   == files["$archiveName/Hello.java"].trim()
            C_SRC      == files["$archiveName/hello.c"].trim()
            CSHARP_SRC == files["$archiveName/Hello.cs"].trim()
    }

    def 'maxDepth with filesystem paths'()
    {
        given:
            createFiles(tempDir, [
                'f1': 1,
                'd1': [
                    'f2': 1,
                    'd2': [
                        'f3': 1,
                        'd3': [
                            'f4': 1
                        ]
                    ]
                ]
            ])

        when:
            def actualEntries = []
            new JWalker()
                .maxDepth(maxDepth)
                .fileTypes(FileType.REGULAR_FILE, FileType.DIRECTORY)
                .walk(tempDir) {
                    displayPath, input, attr ->
                    actualEntries.add(tempDir.relativize(displayPath).toString())
                }

        then:
            expectedEntries.sort(false) == actualEntries.sort(false)

        where:
            maxDepth || expectedEntries
            0        || ['']
            1        || ['', 'f1', 'd1']
            2        || ['', 'f1', 'd1', 'd1/f2', 'd1/d2']
            3        || ['', 'f1', 'd1', 'd1/f2', 'd1/d2', 'd1/d2/f3', 'd1/d2/d3']
            4        || ['', 'f1', 'd1', 'd1/f2', 'd1/d2', 'd1/d2/f3', 'd1/d2/d3', 'd1/d2/d3/f4']
            5        || ['', 'f1', 'd1', 'd1/f2', 'd1/d2', 'd1/d2/f3', 'd1/d2/d3', 'd1/d2/d3/f4']
            6        || ['', 'f1', 'd1', 'd1/f2', 'd1/d2', 'd1/d2/f3', 'd1/d2/d3', 'd1/d2/d3/f4']
    }

    def 'maxDepth with nested archives'()
    {
        // Test an archive file containing various nested archives, as follows:

        // nest-tgz.tgz [archive]
        // |
        // +-- tgz-f1
        // +-- nest-zip.zip [archive]
        // |   |
        // |   +-- zip-f1
        // |   +-- nest-tbz2.tbz2 [archive]
        // |   |   |
        // |   |   +-- bzip2-f1
        // |   |   +-- bzip2-d1/ [dir]
        // |   |       |
        // |   |       +-- bzip2-f2
        // |   |
        // |   +-- zip-d1/ [dir]
        // |       |
        // |       +-- zip-f2
        // |
        // +-- tgz-d1/ [dir]
        //     |
        //     +-- tgz-f2
        //     +-- nest-7z.7z [archive]
        //         |
        //         +-- 7z-f1
        //         +-- 7z-d1/ [dir]
        //             |
        //             +-- 7z-f2

        given:
            def archiveName = 'test-nesting.tgz'
            def archive = tempDir.resolve(archiveName)
            FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(archiveName),
                                            archive.toFile())

        when:
            def actualEntries = []
            new JWalker()
                .maxDepth(maxDepth)
                .fileTypes(FileType.REGULAR_FILE, FileType.ARCHIVE, FileType.DIRECTORY)
                .walk(archive) {
                    displayPath, input, attr ->
                    actualEntries.add(archive.relativize(displayPath).toString())
                }

        then:
            expectedEntries.sort(false) == actualEntries.sort(false)

        where:
            maxDepth || expectedEntries
            0        || ['']
            1        || ['', 'tgz-f1', 'nest-zip.zip', 'tgz-d1']
            2        || ['',
                         'tgz-f1',
                         'nest-zip.zip',
                             'nest-zip.zip/zip-f1',
                             'nest-zip.zip/nest-tbz2.tbz2',
                             'nest-zip.zip/zip-d1',
                         'tgz-d1',
                             'tgz-d1/tgz-f2',
                             'tgz-d1/nest-7z.7z']

            3        || ['',
                         'tgz-f1',
                         'nest-zip.zip',
                             'nest-zip.zip/zip-f1',
                             'nest-zip.zip/nest-tbz2.tbz2',
                                 'nest-zip.zip/nest-tbz2.tbz2/bzip2-f1',
                                 'nest-zip.zip/nest-tbz2.tbz2/bzip2-d1',
                             'nest-zip.zip/zip-d1',
                                 'nest-zip.zip/zip-d1/zip-f2',
                         'tgz-d1',
                             'tgz-d1/tgz-f2',
                             'tgz-d1/nest-7z.7z',
                                 'tgz-d1/nest-7z.7z/7z-f1',
                                 'tgz-d1/nest-7z.7z/7z-d1']

            4        || ['',
                         'tgz-f1',
                         'nest-zip.zip',
                             'nest-zip.zip/zip-f1',
                             'nest-zip.zip/nest-tbz2.tbz2',
                                 'nest-zip.zip/nest-tbz2.tbz2/bzip2-f1',
                                 'nest-zip.zip/nest-tbz2.tbz2/bzip2-d1',
                                     'nest-zip.zip/nest-tbz2.tbz2/bzip2-d1/bzip2-f2',
                             'nest-zip.zip/zip-d1',
                                 'nest-zip.zip/zip-d1/zip-f2',
                         'tgz-d1',
                             'tgz-d1/tgz-f2',
                             'tgz-d1/nest-7z.7z',
                                 'tgz-d1/nest-7z.7z/7z-f1',
                                 'tgz-d1/nest-7z.7z/7z-d1',
                                     'tgz-d1/nest-7z.7z/7z-d1/7z-f2']

            // Same as above
            5        || ['',
                         'tgz-f1',
                         'nest-zip.zip',
                             'nest-zip.zip/zip-f1',
                             'nest-zip.zip/nest-tbz2.tbz2',
                                 'nest-zip.zip/nest-tbz2.tbz2/bzip2-f1',
                                 'nest-zip.zip/nest-tbz2.tbz2/bzip2-d1',
                                     'nest-zip.zip/nest-tbz2.tbz2/bzip2-d1/bzip2-f2',
                             'nest-zip.zip/zip-d1',
                                 'nest-zip.zip/zip-d1/zip-f2',
                         'tgz-d1',
                             'tgz-d1/tgz-f2',
                             'tgz-d1/nest-7z.7z',
                                 'tgz-d1/nest-7z.7z/7z-f1',
                                 'tgz-d1/nest-7z.7z/7z-d1',
                                     'tgz-d1/nest-7z.7z/7z-d1/7z-f2']
    }

    def 'archive file types'()
    {
        given:
            def archiveName = "test-filetypes.${archiveFormat}"
            def archive = tempDir.resolve(archiveName)
            FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(archiveName),
                                            archive.toFile())

        when:
            def actualEntries = [:]
            new JWalker()
                .fileTypes(fileTypes)
                .walk(archive) {
                    displayPath, input, attr ->

                    if(!(attr.isType(FileType.REGULAR_FILE) &&
                         IOUtils.toString(input.get(), StandardCharsets.UTF_8) == "exclude\n"))
                    {
                        actualEntries[archive.relativize(displayPath).toString()] = attr.get(FileAttributes.TYPE)
                    }
                }

        then:
            actualEntries == expectedEntries

        where:
            // TODO: DUMP and RAR formats

            archiveFormat | fileTypes          || expectedEntries
            'ar'          | []                 || [:]
            'ar'          | [REGULAR_FILE]     || ['file': REGULAR_FILE]
            'ar'          | [ARCHIVE]          || ['': ARCHIVE, 'archive.tgz': ARCHIVE]
            'ar'          | [COMPRESSED_FILE]  || ['archive.tgz': COMPRESSED_FILE, 'compressed.gz': COMPRESSED_FILE]
            'ar'          | [DIRECTORY]        || ['dir': DIRECTORY]            // Hack-supported
            'ar'          | [BLOCK_DEVICE]     || ['blockdev': BLOCK_DEVICE]    // Hack-supported
            'ar'          | [CHARACTER_DEVICE] || ['chardev': CHARACTER_DEVICE] // Hack-supported
            'ar'          | [FIFO]             || ['fifo': FIFO]                // Hack-supported
            'ar'          | [SYMBOLIC_LINK]    || ['symlink': SYMBOLIC_LINK]    // Hack-supported
            'ar'          | [SOCKET]           || ['socket': SOCKET]            // Hack-supported
            'ar'          | [WHITEOUT]         || [:] // Not supported
            'ar'          | [HARD_LINK]        || [:] // Not supported
            'ar'          | [NETWORK]          || [:] // Not supported

            // Note: I don't think the AR format -- or perhaps the 'ar' tool -- properly supports
            // any of the DIRECTORY, BLOCK_DEVICE, CHARACTER_DEVICE, FIFO, SYMBOLIC_LINK or SOCKET
            // file types. However, the format does contain an octal mode for each file, which
            // includes the file type, and which IS able to be any of the aforementioned.
            //
            // Thus, to create these types in test-filetypes.ar, I first created an archive
            // containing a series of regular files, then changed their modes in a hex editor.
            // (These are easy to identify by sight, as the metadata is encoded in ASCII. The mode
            // for a regular file will generally be be 100644 (10 for 'regular file', 0644 for
            // permissions). This can be changed to 020644 for block device, for instance.


            'arj'         | []                 || [:]
            'arj'         | [REGULAR_FILE]     || ['file': REGULAR_FILE]
            'arj'         | [ARCHIVE]          || ['': ARCHIVE, 'archive.tgz': ARCHIVE]
            'arj'         | [COMPRESSED_FILE]  || ['archive.tgz': COMPRESSED_FILE, 'compressed.gz': COMPRESSED_FILE]
            'arj'         | [DIRECTORY]        || ['dir': DIRECTORY]
            'arj'         | [BLOCK_DEVICE]     || [:] // Not supported
            'arj'         | [CHARACTER_DEVICE] || [:] // Not supported
            'arj'         | [FIFO]             || [:] // Not supported
            'arj'         | [SYMBOLIC_LINK]    || [:] // Not supported
            'arj'         | [SOCKET]           || [:] // Not supported
            'arj'         | [WHITEOUT]         || [:] // Not supported
            'arj'         | [HARD_LINK]        || [:] // Not supported
            'arj'         | [NETWORK]          || [:] // Not supported

            // Note: the arj command-line tool seems to understand BLOCK_DEVICE, CHARACTER_DEVICE,
            // SYMBOLIC_LINK, HARD_LINK and FIFO (perhaps not SOCKET), but I'm uncertain how to
            // support these.


            'cpio'        | []                 || [:]
            'cpio'        | [REGULAR_FILE]     || ['file': REGULAR_FILE, 'hardlink': REGULAR_FILE]
            'cpio'        | [ARCHIVE]          || ['': ARCHIVE, 'archive.tgz': ARCHIVE]
            'cpio'        | [COMPRESSED_FILE]  || ['archive.tgz': COMPRESSED_FILE, 'compressed.gz': COMPRESSED_FILE]
            'cpio'        | [DIRECTORY]        || ['dir':  DIRECTORY]
            'cpio'        | [BLOCK_DEVICE]     || ['blockdev': BLOCK_DEVICE]
            'cpio'        | [CHARACTER_DEVICE] || ['chardev': CHARACTER_DEVICE]
            'cpio'        | [FIFO]             || ['fifo': FIFO]
            'cpio'        | [SYMBOLIC_LINK]    || ['symlink': SYMBOLIC_LINK]
            'cpio'        | [SOCKET]           || ['socket': SOCKET]
            'cpio'        | [NETWORK]          || ['network': NETWORK]
            'cpio'        | [HARD_LINK]        || [:] // CPIO supports hard links, but lacks a corresponding file type (like TAR).
            'cpio'        | [WHITEOUT]         || [:] // Not supported

            // Note: to create a NETWORK entry in test-filetypes.cpio, I first created a socket
            // entry called 'network', then hex-edited the .cpio file, finding the word 'network',
            // backing up 19 bytes, and changing 'C1' to '91' (the high-order 4 bits representing
            // the file type).


            '7z'          | []                 || [:]
            '7z'          | [REGULAR_FILE]     || ['file': REGULAR_FILE]
            '7z'          | [ARCHIVE]          || ['': ARCHIVE, 'archive.tgz': ARCHIVE]
            '7z'          | [COMPRESSED_FILE]  || ['archive.tgz': COMPRESSED_FILE, 'compressed.gz': COMPRESSED_FILE]
            '7z'          | [DIRECTORY]        || ['dir':  DIRECTORY]
            '7z'          | [WHITEOUT]         || ['file2': WHITEOUT] // "Anti-item" in 7z terminology
            '7z'          | [BLOCK_DEVICE]     || [:] // Not (yet) supported
            '7z'          | [CHARACTER_DEVICE] || [:] // Not (yet) supported
            '7z'          | [FIFO]             || [:] // Not (yet) supported
            '7z'          | [HARD_LINK]        || [:] // Not supported
            '7z'          | [SYMBOLIC_LINK]    || [:] // Not (yet) supported
            '7z'          | [SOCKET]           || [:] // Not (yet) supported
            '7z'          | [NETWORK]          || [:] // Not supported

            // Note: to create a WHITEOUT (anti-item) entry in test-filetypes.7z, I created the
            // initial archive containing an ordinary file ('file2'), then deleted this file from
            // the filesystem, and ran:
            // $ 7z u /path/to/test-filetypes.7z -up3q3 *

            // Note 2: various other file types are "not (yet) supported", because the we could
            // in principle examine the UNIX mode value, but Commons Compress doesn't yet retrieve
            // this.

            // TODO: actually we may be able to handle UNIX special file types here.


            'tar'         | []                 || [:]
            'tar'         | [REGULAR_FILE]     || ['file': REGULAR_FILE]
            'tar'         | [ARCHIVE]          || ['': ARCHIVE, 'archive.tgz': ARCHIVE]
            'tar'         | [COMPRESSED_FILE]  || ['archive.tgz': COMPRESSED_FILE, 'compressed.gz': COMPRESSED_FILE]
            'tar'         | [DIRECTORY]        || ['dir':  DIRECTORY]
            'tar'         | [BLOCK_DEVICE]     || ['blockdev': BLOCK_DEVICE]
            'tar'         | [CHARACTER_DEVICE] || ['chardev': CHARACTER_DEVICE]
            'tar'         | [FIFO]             || ['fifo': FIFO]
            'tar'         | [HARD_LINK]        || ['hardlink': HARD_LINK]
            'tar'         | [SYMBOLIC_LINK]    || ['symlink': SYMBOLIC_LINK]
            'tar'         | [SOCKET]           || [:] // Not (yet) supported
            'tar'         | [WHITEOUT]         || [:] // Not supported
            'tar'         | [NETWORK]          || [:] // Not supported


            'zip'         | []                 || [:]
            'zip'         | [REGULAR_FILE]     || ['file': REGULAR_FILE]
            'zip'         | [ARCHIVE]          || ['': ARCHIVE, 'archive.tgz': ARCHIVE]
            'zip'         | [COMPRESSED_FILE]  || ['archive.tgz': COMPRESSED_FILE, 'compressed.gz': COMPRESSED_FILE]
            'zip'         | [DIRECTORY]        || ['dir':  DIRECTORY]
            'zip'         | [BLOCK_DEVICE]     || ['blockdev': BLOCK_DEVICE]
            'zip'         | [CHARACTER_DEVICE] || ['chardev': CHARACTER_DEVICE]
            'zip'         | [FIFO]             || ['fifo': FIFO]
            'zip'         | [SYMBOLIC_LINK]    || ['symlink': SYMBOLIC_LINK]
            'zip'         | [SOCKET]           || ['socket': SOCKET]
            'zip'         | [HARD_LINK]        || [:] // Not supported (?)
            'zip'         | [WHITEOUT]         || [:] // Not supported
            'zip'         | [NETWORK]          || [:] // Not supported

            // Note: InfoZip couldn't add BLOCK_DEVICE, CHAR_DEVICE, FIFO or SOCKET. I created them
            // by first creating SYMBOLIC_LINK entries, and hex-editing their file types.
            //
            // Specifically, .zip files can (optionally) contain UNIX attributes in the high 16 bits
            // of the "external attributes" field (in the "central directory", towards the _end_ of
            // the file). The relevant 16-bits are located 5-6 bytes prior to each filename. I
            // edited the value A1FF (meaning symlink with 0777 permissions) to 11ff (FIFO), 21ff
            // (CHARACTER_DEVICE), 61ff (BLOCK_DEVICE) and c1ff (SOCKET).
            //
            // (Further note: the "UNIX Extra Field" contains a final, variable-length field used
            // as needed for either a symlink's target, OR a block/char device's major and minor
            // numbers. The latter apparently requires 64 bits (32-bits each), so to ensure that
            // sufficient space is allocated, the original symlinks (to be converted) were created
            // with 8-character-long targets.)
    }

    def 'unix permissions'()
    {
        given:
            def archive = tempDir.resolve(archiveName);
            FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(archiveName),
                                            archive.toFile())

        when:
            def filePermissions = [:]
            new JWalker().walk(archive) {
                displayPath, input, attr ->

                println("ARCHIVE FILE: ${displayPath}, attr=${attr}")

                filePermissions[archive.relativize(displayPath).toString()] =
                    attr.get(FileAttributes.UNIX_PERMISSIONS).toString()
            }

        then:
            // We test cases where:
            // (a) each permission flag is set by itself, and
            // (b) each permission flag is the only one unset.

            // The values 'r', 'w' and 'x' mean, of course, 'read', 'write', and 'execute'. There
            // are other special values too:
            // * 's' means 'set user/group ID';
            // * 'S' means the same, but where the execute permission is missing;
            // * 't' means the sticky bit;
            // * 'T' means the sticky bit where the other execute permission is missing.

            // The test files (on the left) have been named for their permissions (on the right), in
            // a slightly more verbose fashion. They (the expected filenames and permissions) should
            // match each other, of course, as well as matching the actual filenames and
            // permissions.

            filePermissions == ['___.___.___.r__': '------r--',
                                '___.___.r__.___': '---r-----',
                                '___.r__.___.___': 'r--------',
                                '_s_.___.___.___': '-----S---',
                                's__.___.___.___': '--S------',
                                'ss_.rwx.rwx.rwx': 'rwsrwsrwx',
                                'sst.rw_.rwx.rwx': 'rwSrwsrwt',
                                'sst.rwx.rw_.rwx': 'rwsrwSrwt',
                                'sst.rwx.rwx.rw_': 'rwsrwsrwT',
                                'sst.rwx.rwx.r_x': 'rwsrwsr-t',
                                'sst.rwx.rwx._wx': 'rwsrws-wt',
                                'sst.rwx.r_x.rwx': 'rwsr-srwt',
                                'sst.rwx._wx.rwx': 'rws-wsrwt',
                                'sst.r_x.rwx.rwx': 'r-srwsrwt',
                                'sst._wx.rwx.rwx': '-wsrwsrwt',
                                '_st.rwx.rwx.rwx': 'rwxrwsrwt',
                                's_t.rwx.rwx.rwx': 'rwsrwxrwt',
                                '__t.___.___.___': '--------T',
                                '___.___.___._w_': '-------w-',
                                '___.___._w_.___': '----w----',
                                '___._w_.___.___': '-w-------',
                                '___.___.___.__x': '--------x',
                                '___.___.__x.___': '-----x---',
                                '___.__x.___.___': '--x------']

        where:
            archiveName             | _
            'test-permissions.ar'   | _
            'test-permissions.arj'  | _
            'test-permissions.cpio' | _
            'test-permissions.tar'  | _
            'test-permissions.zip'  | _

            // We can't (yet) retrieve permissions from 7z files; waiting on Commons Compress.
            //'test-permissions.7z'   | _

            // We don't (yet) retrieve permissions from RAR files. To do so, we'd need to do
            // in-house parsing, rather than calling the 'unrar' extraction tool. With the latter,
            // we're at the mercy of the actual filesystem, and hence OS-dependent.
            //'test-permissions.rar'  | _

            // I haven't yet included DUMP, just due to the intricacy of creating the test data.
            // 'test-permissions.dump' | _
    }
}
