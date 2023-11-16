package au.djac.jwalker.clitools;
import au.djac.jwalker.*;
import au.djac.jwalker.tree.*;
import au.djac.jprinttree.*;

import picocli.CommandLine;

import java.util.concurrent.Callable;
import java.io.*;

import java.util.stream.*;


@CommandLine.Command(name = "artree",
                     mixinStandardHelpOptions = true,
                     version = "0.1",
                     description = "Prints a file tree, transparently recursing into archives (ZIP, TAR, etc.)",
                     footer = "Copyright (c) 2023 by David J A Cooper.")

public class ArTree implements Callable<Integer>
{
    public static void main(String[] args)
    {
        System.exit(new CommandLine(new ArTree()).execute(args));
    }

    @CommandLine.Parameters(index = "0", description = "Root of the tree to display.", defaultValue = ".")
    private File rootFile;

    // @CommandLine.Option(names = {"-a", "--ascii"},
    //                     description = "Use standard ASCII symbols only (if non-ASCII box-drawing symbols don't display properly).")
    // private boolean ascii;

    @Override
    public Integer call() throws IOException
    {
        // var out = new TreePrintStream(System.out).preNodeLines(0);//.asciiLines();
        //
        // out.println("abc");
        // out.startNode(false);
        // out.println("def");
        // out.startNode(false);
        // out.println("ghi");
        // out.startNode(true);
        // out.println("x\ny\nz");
        // out.startNode(true);
        // out.println("x");
        // out.startNode(true);
        // out.println("x");
        // out.startNode(true);
        // out.println("x");
        // out.endNode();
        // out.endNode();
        // out.endNode();
        // out.endNode();
        // out.endNode();
        // out.startNode(true);
        // out.println("jkl");
        // out.endNode();
        // out.endNode();
        // out.startNode(true);
        // out.println("mno");
        // out.endNode();
        //
        // out.printTree(5, n -> IntStream.range(0, n).boxed().collect(Collectors.toList()), String::valueOf);
        //
        // out.printTree("hello", s -> IntStream.range(1, s.length()).mapToObj(s::substring).collect(Collectors.toList()), String::valueOf);

        var rootPath = rootFile.toPath();

        var tree = new JWalker().makeTree(rootPath);

        var out = new TreePrintStream(System.out).preNodeLines(0);

        out.printTree(tree.getRoot(),
                      FileTreeNode::getChildren,
                      FileTreeNode::getName);

        return 0;
    }
}
