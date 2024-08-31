package au.djac.jwalker.tree;
import au.djac.jwalker.attr.FileAttributes;

import java.nio.file.Path;
import java.util.*;

public class FileTree
{
    public static class ErrorRecord
    {
        private Path path;
        private String message;
        private Exception exception;
        private FileTreeNode node;

        public ErrorRecord(Path path, String message, Exception exception, FileTreeNode node)
        {
            this.path = path;
            this.message = message;
            this.exception = exception;
            this.node = node;
        }

        public Path getPath()           { return path; }
        public String getMessage()      { return message; }
        public Exception getException() { return exception; }
        public FileTreeNode getNode()   { return node; }
    }

    private Map<Path,FileTreeNode> nodeMap = new HashMap<>();
    private FileTreeNode rootNode;
    private List<ErrorRecord> errors = null;

    public FileTree(Path rootPath)
    {
        this.rootNode = new FileTreeNode(rootPath, null);
    }

    public FileTreeNode getRoot()
    {
        return rootNode;
    }

    public FileTreeNode getNode(Path path)
    {
        return nodeMap.get(path);
    }

    public void addPath(Path path, FileAttributes attr)
    {
        if(nodeMap.containsKey(path))
        {
            throw new IllegalStateException("Path '" + path + "' already in tree");
        }

        var currentNode = rootNode;
        var rootPath = rootNode.getPath();
        var pathLen = rootPath.getNameCount() + 1;

        for(var pathComponent : rootPath.relativize(path))
        {
            var childNode = currentNode.getChild(pathComponent.toString());
            if(childNode == null)
            {
                childNode = new FileTreeNode(path.subpath(0, pathLen), null);
                currentNode.addChild(childNode);
            }

            currentNode = childNode;
            pathLen++;
        }
        currentNode.setAttr(attr);
        nodeMap.put(path, currentNode);
    }

    public void addError(Path path, String message, Exception ex) throws RuntimeException
    {
        if(Arrays.stream(ex.getStackTrace()).anyMatch(ste -> ste.getClassName().equals(getClass().getName())))
        {
            throw new RuntimeException(ex);
        }

        if(errors == null)
        {
            errors = new ArrayList<>();
        }
        errors.add(new ErrorRecord(path, message, ex, nodeMap.get(path)));
    }

    public boolean errorsFound()
    {
        return errors != null;
    }

    public List<ErrorRecord> getErrors()
    {
        return Collections.unmodifiableList(errors);
    }
}
