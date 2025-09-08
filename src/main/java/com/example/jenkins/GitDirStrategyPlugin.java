package com.example.jenkins;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TransientProjectActionFactory;
import jenkins.model.Jenkins;

import java.util.Collection;
import java.util.Collections;

/**
 * Hauptklasse für das Git Directory Strategy Plugin.
 * Erweitert Multibranch Pipelines um die Möglichkeit, Builds basierend auf Änderungen in bestimmten Verzeichnissen zu filtern.
 */
@Extension
public class GitDirStrategyPlugin extends TransientProjectActionFactory {

    @Override
    public Collection<? extends Action> createFor(AbstractProject project) {
        return Collections.singleton(new GitDirStrategyAction(project));
    }
}

