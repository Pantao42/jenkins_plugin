package com.example.jenkins;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Build-Strategie für Multibranch Pipelines, die Builds basierend auf Änderungen in bestimmten Verzeichnissen filtert.
 * Verwendet reguläre Ausdrücke für die Verzeichnisspezifikation und prüft Änderungen über die GitHub API.
 */
public class GitDirectoryChangeBuildStrategy extends BranchBuildStrategy {

    private static final Logger LOGGER = Logger.getLogger(GitDirectoryChangeBuildStrategy.class.getName());

    private final List<String> directoryRegexes;
    private final String credentialsId;
    private final String githubApiUrl;
    private final boolean buildOnBranchChange;
    private final boolean buildOnPullRequestChange;

    private transient volatile List<Pattern> compiledPatterns;

    @DataBoundConstructor
    public GitDirectoryChangeBuildStrategy(List<String> directoryRegexes, String credentialsId, String githubApiUrl,
                                         boolean buildOnBranchChange, boolean buildOnPullRequestChange) {
        this.directoryRegexes = directoryRegexes == null ? new ArrayList<>() : new ArrayList<>(directoryRegexes);
        this.directoryRegexes.removeIf(r -> r == null || r.trim().isEmpty());
        this.credentialsId = credentialsId;
        this.githubApiUrl = githubApiUrl != null ? githubApiUrl : "https://api.github.com";
        this.buildOnBranchChange = buildOnBranchChange;
        this.buildOnPullRequestChange = buildOnPullRequestChange;
    }

    public List<String> getDirectoryRegexes() {
        return directoryRegexes;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getGithubApiUrl() {
        return githubApiUrl;
    }

    public boolean isBuildOnBranchChange() {
        return buildOnBranchChange;
    }

    public boolean isBuildOnPullRequestChange() {
        return buildOnPullRequestChange;
    }

    @Override
    public boolean isAutomaticBuild(SCMSource source, SCMHead head, SCMRevision currRevision, SCMRevision prevRevision, SCMRevision lastSeenRevision, TaskListener listener) {
        if (directoryRegexes == null || directoryRegexes.isEmpty()) {
            LOGGER.fine("Keine Verzeichnis-Regexe konfiguriert, Build wird durchgeführt");
            return true;
        }

        if (!(source instanceof GitHubSCMSource)) {
            LOGGER.warning("Quelle ist kein GitHubSCMSource, Build wird durchgeführt");
            return true;
        }

        GitHubSCMSource gitHubSource = (GitHubSCMSource) source;
        
        // Prüfe ob es sich um einen Branch oder PR handelt
        if (head instanceof org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead && !buildOnPullRequestChange) {
            LOGGER.fine("PR-Build ist deaktiviert");
            return false;
        }
        
        if (!(head instanceof org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead) && !buildOnBranchChange) {
            LOGGER.fine("Branch-Build ist deaktiviert");
            return false;
        }

        try {
            return hasChangesInDirectories(gitHubSource, head, currRevision, prevRevision);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Prüfen der Verzeichnisänderungen, Build wird durchgeführt", e);
            return true; // Fail-open: Bei Fehlern wird der Build durchgeführt
        }
    }

    private boolean hasChangesInDirectories(GitHubSCMSource source, SCMHead head, SCMRevision currRevision, SCMRevision prevRevision) 
            throws IOException {
        
        if (prevRevision == null) {
            LOGGER.fine("Kein vorheriger Revision vorhanden, Build wird durchgeführt");
            return true;
        }

        String currSha = getHash(currRevision);
        String prevSha = getHash(prevRevision);
        if (currSha == null || prevSha == null) {
            LOGGER.warning("Commit-Hash konnte nicht ermittelt werden, Build wird durchgeführt");
            return true;
        }

        String token = getGitHubToken(source);
        if (token == null) {
            LOGGER.warning("Kein GitHub Token verfügbar, Build wird durchgeführt");
            return true;
        }

        GitHub github = new GitHubBuilder()
                .withEndpoint(githubApiUrl)
                .withOAuthToken(token)
                .build();

        String repoOwner = source.getRepoOwner();
        String repository = source.getRepository();
        GHRepository ghRepo = github.getRepository(repoOwner + "/" + repository);

        return hasChangesInDirectories(ghRepo, prevSha, currSha);
    }

    /**
     * Prüft, ob sich zwischen zwei Commits Dateien geändert haben, deren Pfad auf eines der
     * konfigurierten Verzeichnis-Regexe passt. Von der Verbindungs-/Token-Logik getrennt,
     * damit dieser Teil isoliert (z.B. mit einem gemockten GHRepository) getestet werden kann.
     */
    boolean hasChangesInDirectories(GHRepository ghRepo, String prevSha, String currSha) throws IOException {
        LOGGER.fine("Prüfe Änderungen zwischen " + prevSha + " und " + currSha);

        GHCompare compare = ghRepo.getCompare(prevSha, currSha);
        GHCommit.File[] files = compare.getFiles();
        if (files == null || files.length == 0) {
            LOGGER.fine("Keine Dateiänderungen zwischen den Revisionen gefunden");
            return false;
        }

        for (GHCommit.File file : files) {
            String fileName = file.getFileName();
            if (fileName == null) {
                continue;
            }
            LOGGER.finest("Prüfe Datei: " + fileName);

            if (matchesAny(fileName, getCompiledPatterns())) {
                LOGGER.fine("Datei " + fileName + " matcht einen konfigurierten Regex, Build wird durchgeführt");
                return true;
            }
        }

        LOGGER.fine("Keine Änderungen in den konfigurierten Verzeichnissen gefunden");
        return false;
    }

    private static String getHash(SCMRevision revision) {
        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            return ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash();
        }
        return null;
    }

    private List<Pattern> getCompiledPatterns() {
        List<Pattern> result = compiledPatterns;
        if (result == null) {
            result = new ArrayList<>();
            for (String regex : directoryRegexes) {
                try {
                    result.add(Pattern.compile(regex));
                } catch (PatternSyntaxException e) {
                    LOGGER.log(Level.WARNING, "Ungültige Regex übersprungen: " + regex, e);
                }
            }
            compiledPatterns = result;
        }
        return result;
    }

    static boolean matchesAny(String fileName, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(fileName).find()) {
                return true;
            }
        }
        return false;
    }

    private String getGitHubToken(GitHubSCMSource source) {
        // Verwende zuerst die konfigurierten Credentials
        if (credentialsId != null && !credentialsId.isEmpty()) {
            StringCredentials credentials = CredentialsProvider.findCredentialById(
                credentialsId, 
                StringCredentials.class, 
                null
            );
            if (credentials != null) {
                return credentials.getSecret().getPlainText();
            }
        }

        // Fallback: Verwende die Credentials der GitHubSCMSource
        String sourceCredentialsId = source.getCredentialsId();
        if (sourceCredentialsId != null && !sourceCredentialsId.isEmpty()) {
            StringCredentials credentials = CredentialsProvider.findCredentialById(
                sourceCredentialsId, 
                StringCredentials.class, 
                null
            );
            if (credentials != null) {
                return credentials.getSecret().getPlainText();
            }
        }

        return null;
    }

    @Extension
    public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

        public String getDisplayName() {
            return "Nur bauen bei Änderungen in Verzeichnissen (Regex)";
        }

        public boolean isApplicable(SCMSource source) {
            return source instanceof GitHubSCMSource;
        }

        public StandardListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
            if (!Jenkins.get().hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }
            StandardListBoxModel model = new StandardListBoxModel();
            model.withEmptySelection();
            model.withMatching(
                    CredentialsMatchers.anyOf(
                            CredentialsMatchers.instanceOf(StringCredentials.class)
                    ),
                    CredentialsProvider.lookupCredentials(
                            StandardCredentials.class,
                            Jenkins.get(),
                            null,
                            URIRequirementBuilder.create().build()
                    )
            );
            return model;
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok("Verwendet die Credentials der GitHub-Quelle");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckDirectoryRegexes(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.ok("Ohne Einträge wird immer gebaut");
            }
            for (String line : value.split("\\r?\\n")) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Pattern.compile(line);
                } catch (PatternSyntaxException e) {
                    return FormValidation.error("Ungültige Regex: " + e.getMessage());
                }
            }
            return FormValidation.ok();
        }
    }
}
