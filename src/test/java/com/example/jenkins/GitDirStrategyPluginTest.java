package com.example.jenkins;

import org.junit.Test;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für das Git Directory Strategy Plugin.
 */
public class GitDirStrategyPluginTest {

    @Test
    public void testPluginCreation() {
        GitDirStrategyPlugin plugin = new GitDirStrategyPlugin();
        assertNotNull("Plugin sollte erstellt werden können", plugin);
    }

    @Test
    public void testActionCreation() {
        // Mock-Projekt für Test
        GitDirStrategyAction action = new GitDirStrategyAction(null);
        assertNotNull("Action sollte erstellt werden können", action);
        assertEquals("Display Name sollte korrekt sein", "Git Directory Strategy", action.getDisplayName());
        assertEquals("URL Name sollte korrekt sein", "git-dir-strategy", action.getUrlName());
        assertEquals("Icon File Name sollte korrekt sein", "document.png", action.getIconFileName());
    }

    @Test
    public void testActionExecute() {
        GitDirStrategyAction action = new GitDirStrategyAction(null);

        // Test der Action-Methoden
        assertNotNull("getDisplayName sollte nicht null zurückgeben", action.getDisplayName());
        assertNotNull("getUrlName sollte nicht null zurückgeben", action.getUrlName());
        assertNotNull("getIconFileName sollte nicht null zurückgeben", action.getIconFileName());
    }

    @Test
    public void testMatchesAnyFindsPartialMatch() {
        List<Pattern> patterns = Collections.singletonList(Pattern.compile("^src/"));
        assertTrue("Datei unter src/ sollte matchen (find statt matches)",
                GitDirectoryChangeBuildStrategy.matchesAny("src/main/Foo.java", patterns));
    }

    @Test
    public void testMatchesAnyIgnoresOtherDirectories() {
        List<Pattern> patterns = Collections.singletonList(Pattern.compile("^src/"));
        assertFalse("Datei unter docs/ sollte nicht matchen",
                GitDirectoryChangeBuildStrategy.matchesAny("docs/readme.md", patterns));
    }

    @Test
    public void testMatchesAnyWithFilenameRegexExample() {
        List<Pattern> patterns = Collections.singletonList(Pattern.compile("^config/.*\\.ya?ml$"));
        assertTrue("config/app.yml sollte matchen",
                GitDirectoryChangeBuildStrategy.matchesAny("config/app.yml", patterns));
        assertFalse("config/app.json sollte nicht matchen",
                GitDirectoryChangeBuildStrategy.matchesAny("config/app.json", patterns));
    }

    @Test
    public void testMatchesAnyWithEmptyList() {
        assertFalse("Leere Liste sollte nicht matchen",
                GitDirectoryChangeBuildStrategy.matchesAny("src/Foo.java", Collections.emptyList()));
    }

    private static GitDirectoryChangeBuildStrategy strategyWithRegexes(String... regexes) {
        return new GitDirectoryChangeBuildStrategy(
                Arrays.asList(regexes), null, null, true, true);
    }

    private static GHCommit.File mockFile(String fileName) {
        GHCommit.File file = mock(GHCommit.File.class);
        when(file.getFileName()).thenReturn(fileName);
        return file;
    }

    @Test
    public void testHasChangesInDirectoriesReturnsTrueOnMatchingFile() throws Exception {
        GitDirectoryChangeBuildStrategy strategy = strategyWithRegexes("^src/");

        GHCommit.File unrelated = mockFile("docs/readme.md");
        GHCommit.File matching = mockFile("src/main/Foo.java");

        GHRepository repo = mock(GHRepository.class);
        GHCompare compare = mock(GHCompare.class);
        when(repo.getCompare(eq("prevSha"), eq("currSha"))).thenReturn(compare);
        when(compare.getFiles()).thenReturn(new GHCommit.File[] { unrelated, matching });

        assertTrue("Änderung unter src/ sollte einen Build auslösen",
                strategy.hasChangesInDirectories(repo, "prevSha", "currSha"));
        verify(repo).getCompare("prevSha", "currSha");
    }

    @Test
    public void testHasChangesInDirectoriesReturnsFalseWhenNoFileMatches() throws Exception {
        GitDirectoryChangeBuildStrategy strategy = strategyWithRegexes("^src/");

        GHCommit.File file1 = mockFile("docs/readme.md");
        GHCommit.File file2 = mockFile("config/app.json");

        GHRepository repo = mock(GHRepository.class);
        GHCompare compare = mock(GHCompare.class);
        when(repo.getCompare(anyString(), anyString())).thenReturn(compare);
        when(compare.getFiles()).thenReturn(new GHCommit.File[] { file1, file2 });

        assertFalse("Ohne Treffer im Diff sollte kein Build ausgelöst werden",
                strategy.hasChangesInDirectories(repo, "prevSha", "currSha"));
    }

    @Test
    public void testHasChangesInDirectoriesReturnsFalseWhenDiffIsEmpty() throws Exception {
        GitDirectoryChangeBuildStrategy strategy = strategyWithRegexes("^src/");

        GHRepository repo = mock(GHRepository.class);
        GHCompare compare = mock(GHCompare.class);
        when(repo.getCompare(anyString(), anyString())).thenReturn(compare);
        when(compare.getFiles()).thenReturn(new GHCommit.File[0]);

        assertFalse("Leerer Diff sollte keinen Build auslösen",
                strategy.hasChangesInDirectories(repo, "prevSha", "currSha"));
    }

    @Test
    public void testHasChangesInDirectoriesUsesCompareOverFullRange() throws Exception {
        // Deckt den früheren Bug ab, bei dem nur der letzte Commit statt der
        // gesamten Spanne prevSha..currSha geprüft wurde.
        GitDirectoryChangeBuildStrategy strategy = strategyWithRegexes("^src/");

        GHCommit.File changed = mockFile("src/Changed.java");

        GHRepository repo = mock(GHRepository.class);
        GHCompare compare = mock(GHCompare.class);
        when(repo.getCompare(eq("abc111"), eq("def999"))).thenReturn(compare);
        when(compare.getFiles()).thenReturn(new GHCommit.File[] { changed });

        assertTrue(strategy.hasChangesInDirectories(repo, "abc111", "def999"));
        verify(repo, never()).getCommit(anyString());
    }
}