package sergio.mira.buildoutputanalyzer;

import com.google.common.collect.Lists;
import hudson.model.Run;
import java.util.List;
import java.util.regex.Matcher;

public class BuildOutputAnalyzer {

    private final Run<?, ?> masterBuild;
    private final List<Entry> entriesOnce;
    private final List<Entry> entriesMultiple;

    private final List<Result> results;

    public BuildOutputAnalyzer(Run<?, ?> build, List<Entry> entries) {
        this.masterBuild = build;
        this.entriesOnce = Lists.newArrayList();
        this.entriesMultiple = Lists.newArrayList();
        this.results = Lists.newArrayList();

        entries.forEach(e -> {
            if (e.stringMatcher != null || e.pattern != null) {
                if (e.multiple) {
                    this.entriesMultiple.add(e);
                } else {
                    if (!e.alreadyFound) {
                        this.entriesOnce.add(e);
                    }
                }
            }
        });
    }

    void processWorkflowRunLine(String line, long currentLine) {
        if (line != null) {
            // Run entries that only have to be found once
            List<Entry> remove = null;
            for(int i = 0; i < entriesOnce.size(); i++) {
                Entry entry = entriesOnce.get(i);
                boolean found = searchForEntry(masterBuild, "", entry, line, currentLine);
                if (found) {
                    if (remove == null) {
                        remove = Lists.newArrayList();
                    }
                    entry.alreadyFound = true;
                    remove.add(entry);
                }
            }

            if (remove != null) {
                entriesOnce.removeAll(remove);
            }

            // Run entries that can be run multiple times
            entriesMultiple.forEach(e -> {
                searchForEntry(masterBuild, "", e, line, currentLine);
            });
        }
    }

    void processLine(Run<?, ?> build, String line, long currentLine) {
        if (line != null) {
            // Run entries that only have to be found once
            entriesOnce.forEach(e -> {
                if (!e.alreadyFound) {
                    if (searchForEntry(build, build.getUrl(), e, line, currentLine)) {
                        e.alreadyFound = true;
                    }
                }
            });

            // Run entries that can be run multiple times
            entriesMultiple.forEach(e -> {
                searchForEntry(build, build.getUrl(), e, line, currentLine);
            });
        }
    }

    boolean searchForEntry(Run<?, ?> build, String buildUrl, Entry entry, String line, long currentLine) {
        if (entry.stringMatcher != null) {
            if (!line.contains(entry.stringMatcher)) {
                return false;
            }
        }

        Matcher matcher = null;
        if (entry.pattern != null) {
            matcher = entry.pattern.matcher(line);
            if (!matcher.find()) {
                return false;
            }
        }

        String message = entry.message;
        if (matcher != null) {
            // Found via regexp
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
        } else {
            // Found via string
            if (message == null) {
                message = line;
            }
        }

        results.add(new Result(currentLine, entry.levelType, message, buildUrl));
        if (entry.failBuild) {
            build.setResult(hudson.model.Result.FAILURE);
        }

        return true;
    }

    public void addAction(Run<?, ?> build) {
        // Add an action to the build with the summary
        if (!results.isEmpty()) {
            build.addAction(new BuildAnalyzerAction(Lists.newArrayList(results)));
        }
    }

    public List<Result> getResults() {
        return results;
    }
}
