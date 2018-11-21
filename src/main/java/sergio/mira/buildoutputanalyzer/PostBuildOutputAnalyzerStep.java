package sergio.mira.buildoutputanalyzer;

import hudson.AbortException;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Run;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.inject.Inject;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundSetter;
import java.util.logging.Logger;

public class PostBuildOutputAnalyzerStep extends AbstractStepImpl {

    private transient static final Logger LOGGER = Logger.getLogger(PostBuildOutputAnalyzerStep.class.getName());
    
    private List<Entry> entries;
    private List<RunWrapper> builds;

    @DataBoundConstructor
    public PostBuildOutputAnalyzerStep() {
    }

    public List<Entry> getEntries() {
        return entries;
    }
    
    @DataBoundSetter
    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public Object getBuilds() {
        return builds;
    }
    
    @DataBoundSetter
    public void setBuilds(List<RunWrapper> builds) {
        this.builds = builds;
    }

    public static class ExecutionImpl extends AbstractStepExecutionImpl {

        @Inject
        private transient PostBuildOutputAnalyzerStep step;
        
        private static final long serialVersionUID = 1L;

        @Override
        public boolean start() throws Exception {            
            StepContext context = getContext();
            Run<?, ?> build = context.get(Run.class);
            
            Jenkins instance = Jenkins.getInstanceOrNull();
            if (instance != null) {
                BuildOutputAnalyzer analyzer = new BuildOutputAnalyzer(build, step.entries);
                ExecutorService executor = Executors.newFixedThreadPool(10);
                step.builds.forEach(b -> {
                    try {
                        AbstractProject project = (AbstractProject) instance.getItemByFullName(b.getProjectName());
                        if (project != null) {
                            final Run run = project.getBuildByNumber(b.getNumber());
                            if (run != null) {
                                executor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            BufferedReader reader = new BufferedReader(run.getLogReader());
                                            String line;
                                            long lineCount = 0;
                                            while ((line = reader.readLine()) != null) {
                                                lineCount++; 
                                                analyzer.processLine(run, line, lineCount);
                                            }
                                            reader.close();
                                        } catch (IOException ex) {
                                            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                                        }
                                    }
                                });
                            }
                        }
                    } catch(AbortException exp) {
                        LOGGER.log(Level.SEVERE, exp.getMessage(), exp);
                    }
                });
                
                executor.shutdown();
                executor.awaitTermination(1, TimeUnit.DAYS);
                
                // Add the new results to the existing action
                BuildAnalyzerAction existingAction = build.getAction(BuildAnalyzerAction.class);
                if (existingAction != null) {
                    existingAction.getResults().addAll(analyzer.getResults());
                } else {
                    build.addAction(new BuildAnalyzerAction(analyzer.getResults()));
                }
            }
            
            context.onSuccess(null);
            return true;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
            getContext().onFailure(cause);
        }
    }

    @Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(ExecutionImpl.class);
        }

        @Override
        public String getDisplayName() {
            return "PostBuildOutputAnalyzer";
        }

        @Override
        public String getFunctionName() {
            return "postBuildOutputAnalyzer";
        }
    }
}
