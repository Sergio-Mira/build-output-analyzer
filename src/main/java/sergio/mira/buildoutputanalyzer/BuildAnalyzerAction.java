package sergio.mira.buildoutputanalyzer;

import hudson.model.Action;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.export.Exported;

public final class BuildAnalyzerAction implements Action {
    
    private final List<Result> results;
    
    private List<Result> errorResults;
    private List<Result> warningResults;
    private List<Result> infoResults;
    
    /**
     * Create an instance of the build analyzer action.
     * @param results Results of the search in the logs
     */
    public BuildAnalyzerAction(final List<Result> results) {
        super();
        this.results = results;
    }

    /**
     * Gets the action display name.
     * @return the display name
     */
    @Override
    public String getDisplayName() {
        return "Build Analyzer";
    }

    /**
     * This action doesn't provide any icon file.
     * @return null
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * Gets the URL name for this action.
     * @return the URL name
     */
    @Override
    public String getUrlName() {
        return "buildanalyzer";
    }
    
    /**
     * Gets the results of the analysis.
     * @return the results of the analysis
     */
    @Exported
    public List<Result> getResults() {
        return results;
    }
    
    private void rebuildFilteredResults() {
        if (errorResults == null) {
            List<Result> r = new ArrayList<Result>();
            for(Result result : results) {
                if (result.getLevelType().equalsIgnoreCase("error")) {
                    r.add(result);
                }
            }
            errorResults = r;
        }
        
        if (warningResults == null) {
            List<Result> r = new ArrayList<Result>();
            for(Result result : results) {
                if (result.getLevelType().equalsIgnoreCase("warning")) {
                    r.add(result);
                }
            }
            warningResults = r;
        }
        
        if (infoResults == null) {
            List<Result> r = new ArrayList<Result>();
            for(Result result : results) {
                if (result.getLevelType().equalsIgnoreCase("info")) {
                    r.add(result);
                }
            }
            infoResults = r;
        }
    }
    
    /**
     * Gets the results of the analysis.
     * @return the results of the analysis
     */
    @Exported
    public List<Result> getErrorResults() {
        rebuildFilteredResults();
        return errorResults;
    }
    
    /**
     * Gets the results of the analysis.
     * @return the results of the analysis
     */
    @Exported
    public List<Result> getWarningResults() {
        rebuildFilteredResults();
        return warningResults;
    }
    
    /**
     * Gets the results of the analysis.
     * @return the results of the analysis
     */
    @Exported
    public List<Result> getInfoResults() {
        rebuildFilteredResults();
        return infoResults;
    }
}
