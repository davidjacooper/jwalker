package au.djac.jwalker

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import java.nio.charset.*
import java.nio.file.*

import spock.lang.*

// TODO:
// - test singleton file
// - test attributes

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
                'test7z.7z',
                'testarj-uncompressed.arj',
                'testcpio.cpio',
                'testrar.rar',
                'testtar.tar',
                'testtar.tar.br',
                'testtar.tar.bz2',
                'testtar.tar.gz',
//                 'testtar.tar.lz',
                'testtar.tar.lzma',
//                 'testtar.tar.lzo',
                'testtar.tar.sz',
                'testtar.tar.xz',
                'testtar.tar.Z',
                'testtar.tar.zst',
                'testzip.zip'
            ]

            def archiveCopies = [
                ('testtar.tb2'): 'testtar.tar.bz2',
                ('testtar.tbz'): 'testtar.tar.bz2',
                ('testtar.tbz2'): 'testtar.tar.bz2',
                ('testtar.tz2'): 'testtar.tar.bz2',
                ('testtar.taz'): 'testtar.tar.gz',
                ('testtar.tgz'): 'testtar.tar.gz',
                ('testtar.tlz'): 'testtar.tar.lzma',
                ('testtar.txz'): 'testtar.tar.xz',
                ('testtar.tZ'): 'testtar.tar.Z',
                ('testtar.taZ'): 'testtar.tar.Z',
                ('testtar.tzst'): 'testtar.tar.zst',
            ]

        when:
            // Copy archive file to the test directory
            for(name in archives)
            {
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
            def archiveName = 'testar.a'
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
                .fileTypes(FileAttributes.Type.REGULAR_FILE, FileAttributes.Type.DIRECTORY)
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
            def archiveName = 'nest-tgz.tgz'
            def archive = tempDir.resolve(archiveName)
            FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(archiveName),
                                            archive.toFile())

        when:
            def actualEntries = []
            new JWalker()
                .maxDepth(maxDepth)
                .fileTypes(FileAttributes.Type.REGULAR_FILE, FileAttributes.Type.ARCHIVE, FileAttributes.Type.DIRECTORY)
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
}
