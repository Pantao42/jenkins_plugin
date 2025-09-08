package com.example.jenkins;

import org.junit.Test;
import static org.junit.Assert.*;

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
}

