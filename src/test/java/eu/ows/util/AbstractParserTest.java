package eu.ows.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.commons.io.IOUtils;

public class AbstractParserTest {

    protected byte[] readContent(final String filename) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(getClass().getClassLoader().getResourceAsStream(filename), baos);
        return baos.toByteArray();
    }
}
