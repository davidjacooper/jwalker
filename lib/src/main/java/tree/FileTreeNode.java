package au.djac.jwalker.tree;
import au.djac.jwalker.attr.FileAttributes;

import java.nio.file.Path;
import java.util.*;

public class FileTreeNode
{
    private final String name;
    private final Path path;
    private FileAttributes attr;
    private Map<String,FileTreeNode> children = null;

    public FileTreeNode(Path path, FileAttributes attr)
    {
        this.name = path.getName(path.getNameCount() - 1).toString();
        this.path = path;
        this.attr = attr;
    }

    public String getName()         { return name; }
    public Path getPath()           { return path; }
    public FileAttributes getAttr() { return attr; }

    public void setAttr(FileAttributes attr)
    {
        this.attr = attr;
    }

    public FileTreeNode getChild(String child)
    {
        return (children == null) ? null : children.get(child);
    }

    public void addChild(FileTreeNode child)
    {
        var childName = child.getName();
        if(children == null)
        {
            children = new HashMap<>();
        }
        else if(children.containsKey(childName))
        {
            throw new IllegalStateException(
                "FileTreeNode '" + path + "' already has a child entry called '" + childName + "'");
        }
        children.put(childName, child);
    }

    public Collection<FileTreeNode> getChildren()
    {
        return (children == null) ? Collections.emptyList() : children.values();
    }
}
