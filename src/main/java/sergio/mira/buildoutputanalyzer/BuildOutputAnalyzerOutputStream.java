package sergio.mira.buildoutputanalyzer;

import static com.google.common.base.Preconditions.checkNotNull;
import java.io.IOException;
import java.io.OutputStream;

public class BuildOutputAnalyzerOutputStream extends OutputStream {

    private final static byte NEW_LINE_CHARACTER = (byte) 0x0A;
    
    private final OutputStream delegate; 
    private final BuildOutputAnalyzer analyzer;
    
    private final StringBuilder strBuilder;
    private final byte[] oneElementByteArray = new byte[1];
    private long currentLine;

    public BuildOutputAnalyzerOutputStream(OutputStream delegate, BuildOutputAnalyzer analyzer) {
        this.delegate = checkNotNull(delegate);
        this.analyzer = analyzer;
        
        this.strBuilder = new StringBuilder();
        this.currentLine = 1;
    }

    @Override
    public void write(int b) throws IOException {
        oneElementByteArray[0] = (byte)b;
        delegate.write(b);
        processBytes(oneElementByteArray, 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
        processBytes(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
        processBytes(b, off, len);
    }
    
    private void processBytes(byte[] b, int off, int len) {
        if (analyzer != null) {
            for (int i = off; i < off + len; i++) {
                byte rb = b[i];
                if (rb == NEW_LINE_CHARACTER) {
                    String line = strBuilder.toString();
                    analyzer.processWorkflowRunLine(line, currentLine);

                    // Cleanup line
                    strBuilder.delete(0, strBuilder.length());
                    currentLine++;
                } else {
                    strBuilder.append((char)rb);
                }
            }
        }
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
