package sergio.mira.buildoutputanalyzer;

import java.io.Serializable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Line where the entry started.
     */
    private final long line;
    
    /**
     * Level type of the report if found.
     */
    private final String levelType;
    
    /**
     * Message output.
     */
    private final String message;
    
    /**
     * Build where the entry was found.
     */
    private final String buildUrl;
    
    public Result(long line, String levelType, String message, String buildUrl) {
        this.line = line;
        this.levelType = levelType;
        this.message = message;
        this.buildUrl = buildUrl;
    }
    
    @Exported
    public long getLine() {
        return line;
    }
    
    @Exported
    public String getLevelType() {
        return levelType;
    }

    @Exported
    public String getMessage() {
        return message;
    }

    @Exported
    public String getBuildUrl() {
        return buildUrl;
    }
}
