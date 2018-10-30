package sergio.mira.buildoutputanalyzer;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.parameterizedtrigger.BuildInfoExporterAction;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import java.io.BufferedReader;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class BuildAnalyzerRecorder extends Recorder implements SimpleBuildStep {
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    private transient static final Logger LOGGER = Logger.getLogger(Recorder.class.getName());
    private transient final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    private final List<Entry> entries;
    
    private int lineBuffer;
    
    /**
     * Constructs a {@link BuildAnalyzerRecorder}
     * @param entries Search for these entries in the log
     */
    @DataBoundConstructor
    public BuildAnalyzerRecorder(List<Entry> entries) {
        this.entries = entries;
        this.lineBuffer = 10;
    }
    
    @SuppressWarnings("unused")
    public List<Entry> getEntries() {
        return entries;
    }
    
    @SuppressWarnings("unused")
    public int getLineBuffer() {
        return lineBuffer;
    }

    @DataBoundSetter
    public void setLineBuffer(int lineBuffer) {
        this.lineBuffer = lineBuffer;
    }

    /**
     * Perform the publication.
     * @param build
     * 		Build on which to apply publication
     * @param workspacePath
     *          Unused
     * @param launcher
     * 		Unused
     * @param listener
     * 		Unused
     * @throws IOException
     * 		In case of file IO mismatch
     * @throws InterruptedException
     * 		In case of interruption
    */
    @Override
    public void perform(final Run<?, ?> build, final FilePath workspacePath,
                   final Launcher launcher, final TaskListener listener)
                   throws InterruptedException, IOException {
        // WorkflowRuns might not have finished writing the console output
        if (build instanceof WorkflowRun) {
            // Run after the build has been finished and the log is updated, ugh!
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Ugh!
                        for (int t = 0; t < 100; t++) {
                            if (build.isLogUpdated()) {
                                Thread.sleep(100);
                            } else {
                                break;
                            }
                        }
                        performAnalysis(build);
                    } catch(IOException | InterruptedException ex) {
                        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            });
        } else {
            // Other builds execute as postbuild step and everyhing is ready
            performAnalysis(build);
        }
    }
    
    private void performAnalysis(final Run<?, ?> build) throws IOException {
        try {
            List<Result> results = new ArrayList<Result>();
            Set<Run<?, ?>> processedRuns = new HashSet<Run<?, ?>>();
            processedRuns.add(build);
            
            int bufferSize = Math.max(1, lineBuffer);
            // Filtered entries based on build state
            List<Entry> filteredEntries = new ArrayList<Entry>();
            if (build.getResult() == hudson.model.Result.SUCCESS) {
                for (Entry entry : entries) {
                    if (entry.runOnSuccess) {
                        filteredEntries.add(entry);
                    }
                } 
            } else {
                for (Entry entry : entries) {
                    if (entry.runOnFailure) {
                        filteredEntries.add(entry);
                    }
                } 
            }
            
            processRun(build, filteredEntries, bufferSize, results, processedRuns, build.getUrl());

            // Save the results in the build
            if (results.size() > 0) {
                // This renders in summary.jelly
                build.addAction(new BuildAnalyzerAction(results));
            }
        } catch(Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
    
    private void processRun(final Run<?, ?> build, final List<Entry> entries, final int bufferSize,
            final List<Result> results, final Set<Run<?, ?>> processedRuns, final String buildUrl) throws Exception {
        Reader logReader = build.getLogReader();
        BufferedReader reader = new BufferedReader(logReader);
        processFile(build, entries, reader, bufferSize, results, buildUrl);
        
        List<BuildInfoExporterAction> subBuildActions = build.getActions(BuildInfoExporterAction.class);
        if (subBuildActions.size() > 0) {
            for(BuildInfoExporterAction subBuildAction : subBuildActions) {
                for(Run<?, ?> subBuild : subBuildAction.getTriggeredBuilds()) {
                    LOGGER.log(Level.INFO, "Processing child job: " + subBuild.getUrl(), (Object[])null);
                    processRun(subBuild, entries, bufferSize, results, processedRuns, subBuild.getUrl());
                }
            }
        }
    }

    private void processFile(final Run<?, ?> build, final List<Entry> filteredEntries,
            final BufferedReader reader, final int bufferSize, final List<Result> results, final String buildUrl) {
        // Preprocess regexps
        List<Pattern> regexps = new ArrayList();
        for(Entry entry : filteredEntries) {
            Pattern p = Pattern.compile(entry.regex);
            regexps.add(p);
        }
        
        String line;
        int lines = 0;
        long linesAdvanced = 0;
        long advancedIndex = 0;
        Map<Pattern, Long> lastFoundIndex = new HashMap<Pattern, Long>();
            
        if (bufferSize > 1) {
            // Read with a bufferSize lenght of lines
            StringBuilder buffer = new StringBuilder();
            try {
                while ((line = reader.readLine()) != null && lines < bufferSize) {
                    buffer.append(line).append("\n");
                    lines++;
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }

            // First search in buffer
            String bufferStr = buffer.toString();
            processConsoleBuffer(build, filteredEntries, bufferStr, regexps, results, linesAdvanced, advancedIndex, lastFoundIndex, buildUrl);

            // Read line by line, removing the fist one and adding the new one and redoing the search
            try {
                while ((line = reader.readLine()) != null) {
                    // Remove previous first line
                    int indexOfNewLine = buffer.indexOf("\n");
                    buffer.delete(0, indexOfNewLine);
                    buffer.append(line).append("\n");
                    linesAdvanced++;
                    advancedIndex += indexOfNewLine;

                    // First again in the buffer
                    bufferStr = buffer.toString();
                    processConsoleBuffer(build, filteredEntries, bufferStr, regexps, results, linesAdvanced, advancedIndex, lastFoundIndex, buildUrl);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        } else {
            // Read line by line
            try {
                while ((line = reader.readLine()) != null) {
                    linesAdvanced++; 
                    // First again in the buffer
                    processConsoleBuffer(build, filteredEntries, line, regexps, results, linesAdvanced, advancedIndex, lastFoundIndex, buildUrl);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Gets the required monitor service.
     * @return the BuildStepMonitor
     */
    @Override
    public final BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    private void processConsoleBuffer(final Run<?, ?> build, final List<Entry> filteredEntries,
            final String bufferStr, final List<Pattern> regexps, final List<Result> results, 
            final long linesAdvanced, final long advancedIndex, final Map<Pattern, Long> lastFoundIndex, final String buildUrl) {
        for(int i = 0; i < regexps.size(); i++)  {
            Pattern regexp = regexps.get(i);
            if (regexp != null) {
                Matcher matcher = regexp.matcher(bufferStr);
                boolean continueFinding = true;
                while (matcher.find() && continueFinding) {
                    // Check if we had already found this entry (could be done more efficiently)
                    int matchStart = matcher.start();
                    long foundIndex = advancedIndex + matchStart;
                    if (lastFoundIndex.get(regexp) == null ||
                            lastFoundIndex.get(regexp) < foundIndex) {
                        // We haven't seen this match before, save
                        lastFoundIndex.put(regexp, foundIndex);
                        
                        // Find out what line we are in
                        int lineNumber = bufferStr.substring(0, matchStart).split("\n").length;
                        
                        // Process the entry
                        Entry entry = filteredEntries.get(i);
                        String message = entry.message;
                        if (message == null || message.isEmpty()) {
                            // No message configured, take the text found
                            message = matcher.group();
                        } else if (entry.dynamicMessage) {
                            // Replace the %s in the message with the groups found
                            int groupCount = matcher.groupCount();
                            // Create more space so that messages don't fail
                            // if there were no groups in that index
                            String[] groups = new String[Math.max(groupCount, 100)];
                            for (int g = 0 ; g < groupCount; g++) {
                                groups[g] = matcher.group(g);
                            }
                            message = String.format(message, (Object[]) groups);
                        }
                        
                        if (entry.failBuild) {
                            build.setResult(hudson.model.Result.FAILURE);
                        }

                        if (entry.once) {
                            continueFinding = false;
                            regexps.set(i, null);
                        }
                        Result r = new Result(lineNumber + linesAdvanced, entry.levelType, message, buildUrl);
                        results.add(r);
                    }
                }
            }
        }
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl(Class<? extends Publisher> clazz) {
            super(clazz);
            load();
        }

        public DescriptorImpl() {
            this(BuildAnalyzerRecorder.class);
        }

        @Override
        public String getDisplayName() {
            return "Build Output Analyzer";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/buildoutputanalyzer/help.html";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
