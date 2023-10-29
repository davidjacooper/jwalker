package au.djac.jdirscanner;
import java.nio.file.Path;
import java.util.Map;

@FunctionalInterface
public interface FileCallback
{
    public void call(Path displayPath,
                     InputSupplier input,
                     Map<String,String> fileMetadata);
}
