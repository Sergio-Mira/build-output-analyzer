*Pipeline Build Output Analyzer*
Find string/regexp in build output and bring it into the build summary.
Work in progress.


Features
========
- Find string/regular expressions in build output and sub build output
- Customizable level of message: info, warning, error
- Customizable message (if non provided the found text is used)
- Dynamic message making use of regular expression groups (can extract info from the regular expression and use it to construct a human readable message)
- Creates direct link to the console line (if the build can be resolved), usable if the Console Line Number plugin is installed
- Can fail the build if an entry is found

Install
=======

Tested with Jenkins 2.121.3

* Build the plugin
* Upload the HPI file found in the `target` folder to Jenkins under `Plugin Management - Advanced`

Links to console to a specific line will work if you install the plugin:
https://github.com/ericsmalling/console-linenumber-plugin

Building
========

Run `mvn clean install`.

Pipeline example
================
```groovy
analyzerEntries = []
analyzerEntries.add([$class: "Entry", levelType: "info", stringMatcher: "Search for this"])

buildOutputAnalyzer(entries: analyzerEntries) {
  // Your pipeline steps here
}
```

Pipeline example with sub builds
================================
```groovy
analyzerEntries = []
analyzerEntries.add([$class: "Entry", levelType: "info", stringMatcher: "Search for this"])

savedBuilds = []

buildOutputAnalyzer(entries: analyzerEntries) {
  // Your pipeline steps here, this will be checked while it is being written
  
  // Save the build executions for any external builds ran
  savedBuilds.push(build "subjob")
}

// Process post build any external builds that haven been run
postBuildOutputAnalyzer(entries: analyzerEntries, builds: savedBuilds)
```

Screenshot
==========
![Screenshot](screenshot.png)
