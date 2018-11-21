package sergio.mira.buildoutputanalyzer;

import com.google.common.util.concurrent.FutureCallback;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import java.io.FileOutputStream;
import java.util.List;
import javax.inject.Inject;
import jenkins.YesNoMaybe;
import org.kohsuke.stapler.DataBoundSetter;

public class BuildOutputAnalyzerStep extends AbstractStepImpl {

    private List<Entry> entries;

    @DataBoundConstructor
    public BuildOutputAnalyzerStep() {
    }

    public List<Entry> getEntries() {
        return entries;
    }
    
    @DataBoundSetter
    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public static class ExecutionImpl extends AbstractStepExecutionImpl {

        @Inject
        private transient BuildOutputAnalyzerStep step;
        
        private transient BuildOutputAnalyzer analyzer;
        
        private static final long serialVersionUID = 1L;

        @Override
        public boolean start() throws Exception {
            StepContext context = getContext();
            
            Run<?, ?> build = context.get(Run.class);
            analyzer = new BuildOutputAnalyzer(build, step.entries);
            
            context
                    .newBodyInvoker()
                    .withContext(createConsoleLogFilter(context))
                    .withCallback(new BuildOutputAnalyzerExecutionCallback(build, context, analyzer))
                    .start();
            
            return false;
        }

        private ConsoleLogFilter createConsoleLogFilter(StepContext context)
                throws IOException, InterruptedException {
            ConsoleLogFilter original = context.get(ConsoleLogFilter.class);
            ConsoleLogFilter subsequent = new TimestampNotesConsoleLogFilter(analyzer);
            return BodyInvoker.mergeConsoleLogFilters(original, subsequent);
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
            return "BuildOutputAnalyzer";
        }

        @Override
        public String getFunctionName() {
            return "buildOutputAnalyzer";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }

    private static class TimestampNotesConsoleLogFilter extends ConsoleLogFilter
            implements Serializable {

        private static final long serialVersionUID = 1;
        
        private final transient BuildOutputAnalyzer analizer;

        TimestampNotesConsoleLogFilter(BuildOutputAnalyzer analizer) {
            this.analizer = analizer;
        }

        @Override
        public OutputStream decorateLogger(Run build, OutputStream logger) throws IOException, InterruptedException {
            return new BuildOutputAnalyzerOutputStream(logger, analizer);
        }
    }
    
    // Based on BodyExecutionCallback.Wrapper
    private static class BuildOutputAnalyzerExecutionCallback extends BodyExecutionCallback {
        private static final long serialVersionUID = 1L;
        
        private final FutureCallback<Object> v;

        private final transient Run<?, ?> build;
        
        private final transient BuildOutputAnalyzer analyzer;
        
        public BuildOutputAnalyzerExecutionCallback(Run<?, ?> build, FutureCallback<Object> v, BuildOutputAnalyzer analyzer) {
            if (!(v instanceof Serializable))
                throw new IllegalArgumentException(v.getClass() + " is not serializable");
            this.build = build;
            this.v = v;
            this.analyzer = analyzer;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            v.onSuccess(result);
            analyzer.addAction(build);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            v.onFailure(t);
            analyzer.addAction(build);
        }
    }
}
