package rawhttp.core.body;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.OptionalLong;
import javax.annotation.Nullable;

/**
 * A {@link HttpMessageBody} containing the contents of a {@link File}.
 */
public class FileBody extends HttpMessageBody {

    private final File file;

    public FileBody(File file) {
        this(file, null);
    }

    public FileBody(File file,
                    @Nullable String contentType) {
        super(contentType);
        this.file = file;
    }

    /**
     * @return the file associated with this instance.
     */
    public File getFile() {
        return file;
    }

    @Override
    public LazyBodyReader toBodyReader() {
        try {
            return new LazyBodyReader(new BodyType.ContentLength(file.length()),
                    new BufferedInputStream(new FileInputStream(file)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected OptionalLong getContentLength() {
        return OptionalLong.of(file.length());
    }

}
