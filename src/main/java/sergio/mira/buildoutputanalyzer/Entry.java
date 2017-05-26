package sergio.mira.buildoutputanalyzer;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public final class Entry implements Describable<Entry> {
    /**
     * Regular expression that will be searched in the console.
     */
    public String regex;
    
    /**
     * Optional message given to the entry.
     */
    public String message;

    /**
     * Search for this entry once.
     */
    public boolean once = true;
    
    /**
     * Run this check when the build has failed.
     */
    public boolean runOnFailure = true;
    
    /**
     * Run this check when the build has succeeded.
     */
    public boolean runOnSuccess = false;
    
    /**
     * Replace the groups found in the regular expression in the label.
     */
    public boolean dynamicMessage = false;
    
    /**
     * Level type of the report if found.
     */
    public String levelType;
    
    /**
     * Fail build if found.
     */
    public boolean failBuild = false;

    @DataBoundConstructor
    public Entry(String levelType, String regex) {
        this.levelType = levelType;
        this.regex = regex;
        
        this.once = true;
        this.runOnFailure = true;
        this.runOnSuccess = false;
        this.dynamicMessage = false;
        this.failBuild = false;
    }

    @DataBoundSetter
    public void setMessage(String message) {
        this.message = message;
    }

    @DataBoundSetter
    public void setOnce(boolean once) {
        this.once = once;
    }

    @DataBoundSetter
    public void setRunOnFailure(boolean runOnFailure) {
        this.runOnFailure = runOnFailure;
    }

    @DataBoundSetter
    public void setRunOnSuccess(boolean runOnSuccess) {
        this.runOnSuccess = runOnSuccess;
    }

    @DataBoundSetter
    public void setDynamicMessage(boolean dynamicMessage) {
        this.dynamicMessage = dynamicMessage;
    }

    @DataBoundSetter
    public void setFailBuild(boolean failBuild) {
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
        
        public FormValidation doCheckRegex(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("Provide a regular expression");
            }
            
            return FormValidation.ok();
        }
        
        public FormValidation doCheckDynamicMessage(@QueryParameter boolean value, @QueryParameter String message) {
            if (value && (message == null || message.isEmpty())) {
                return FormValidation.error("Provide a message to replace content dynamically");
            }
            
            return FormValidation.ok();
        }
        
        public FormValidation doCheckRunOnFailure(@QueryParameter boolean value, @QueryParameter boolean runOnSuccess) {
            if (!value && !runOnSuccess) {
                return FormValidation.error("The check will never run");
            }
            
            return FormValidation.ok();
        }
        
        public FormValidation doCheckFailBuild(@QueryParameter boolean value, @QueryParameter boolean runOnSuccess) {
            if (value && !runOnSuccess) {
                return FormValidation.error("This check needs to run on success to fail the build");
            }
            
            return FormValidation.ok();
        }
    }
}
