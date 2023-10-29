package au.djac.jdirscanner

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import java.nio.charset.*
import java.nio.file.*

import spock.lang.*

class JDirScannerTest extends Specification
{
    private Path tempDir

    def setup()
    {
        tempDir = Files.createTempDirectory("JDirScannerTest")
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
            def opts = new JDirScannerOptions()
            inclusions.forEach { opts.addInclusion(it) }
            exclusions.forEach { opts.addExclusion(it) }

            def scanner = new JDirScanner(opts)
            def actualFiles = []

            scanner.forEachFile(tempDir) {
                displayPath, input, fileMetadata ->
                actualFiles.add(displayPath.toString())
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

    def 'content and archives'()
    {
        given:
            def archives = [
                'test7z.7z',
//                 'testar.a',
//                 'testarj.arj',
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

            def expectedPy = """
                #!/usr/bin/python
                print("Hello world")
            """.stripIndent().trim()

            def expectedJava = """
                package hello;
                public class Hello
                {
                    public static void main(String[] args)
                    {
                        System.out.println("Hello world");
                    }
                }
            """.stripIndent().trim()

            def expectedC = """
                #include <stdio.h>

                int main(void)
                {
                    printf("Hello world\\n");
                    return 0;
                }
            """.stripIndent().trim()

            def expectedCSharp = """
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

            def scanner = new JDirScanner(new JDirScannerOptions());
            def files = [:]
            scanner.forEachFile(tempDir) {
                displayPath, input, fileMetadata ->

                files[displayPath.toString()] = IOUtils.toString(input.get(), StandardCharsets.UTF_8)
            }

            println(files.keySet())

            def actualPy     = { files["$it/hello.py"].trim() }
            def actualJava   = { files["$it/dir/Hello.java"].trim() }
            def actualC      = { files["$it/dir/hello.c"].trim() }
            def actualCSharp = { files["$it/dir/nested_dir/Hello.cs"].trim() }

        then:
            for(name in archives)
            {
                assert expectedPy     == actualPy(name)
                assert expectedJava   == actualJava(name)
                assert expectedC      == actualC(name)
                assert expectedCSharp == actualCSharp(name)
            }
    }
}
