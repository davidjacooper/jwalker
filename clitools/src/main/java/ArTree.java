package au.djac.jwalker.clitools;
import au.djac.jwalker.*;
import au.djac.jwalker.tree.*;
import au.djac.treewriter.*;

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

    @Override
    public Integer call() throws IOException
    {
        var tree = new JWalker().makeTree(rootFile.toPath());
        var out = new TreeWriter(System.out);
        out.getOptions().topMargin(0);
        out.printTree(tree.getRoot(),
                      FileTreeNode::getChildren,
                      FileTreeNode::getName);
        return 0;
    }
}
