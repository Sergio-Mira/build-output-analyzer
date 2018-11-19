package sergio.mira.buildoutputanalyzer;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.util.regex.Pattern;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public final class Entry implements Describable<Entry> {
    /**
     * Level type of the report if found.
     */
    public final String levelType;
    
    /**
     * String that will be searched in the console.
     */
    public final String stringMatcher;
    
    /**
     * Regular expression that will be searched in the console.
     */
    public final String regexMatcher;
    
    /**
     * Optional message given to the entry.
     */
    public final String message;

    /**
     * Search for this entry once.
     */
    public final boolean once;
    
    /**
     * Replace the groups found in the regular expression in the label.
     */
    public final boolean dynamicMessage;
    
    /**
     * Fail build if found.
     */
    public final boolean failBuild;
    
    // Compiled regexp Pattern
    public final Pattern pattern;

    @DataBoundConstructor
    public Entry(String levelType, String stringMatcher, String regexMatcher, String message, boolean once, boolean dynamicMessage, boolean failBuild) {
        this.levelType = levelType;
        this.stringMatcher = stringMatcher;
        this.regexMatcher = regexMatcher;
        if (this.regexMatcher == null) {
            this.pattern = null;
        } else {
            this.pattern = Pattern.compile(this.regexMatcher);
        }
        this.message = message;
        this.once = once;
        this.dynamicMessage = dynamicMessage;
        this.failBuild = failBuild;
    }

    @Override
    public Descriptor<Entry> getDescriptor() {
        return DESCRIPOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<Entry> {

        @Override
        public String getDisplayName() {
            return "";
        }
        
        public FormValidation doCheckDynamicMessage(@QueryParameter boolean value, @QueryParameter String message) {
            if (value && (message == null || message.isEmpty())) {
                return FormValidation.error("Provide a message to replace content dynamically");
            }
            
            return FormValidation.ok();
        }
    }
}
