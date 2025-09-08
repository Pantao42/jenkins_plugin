package com.example.jenkins;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Run;

/**
 * Action für das Git Directory Strategy Plugin.
 * Zeigt Informationen über die Git Directory Strategy Konfiguration an.
 */
public class GitDirStrategyAction implements Action {

    private final AbstractProject<?, ?> project;

    public GitDirStrategyAction(AbstractProject<?, ?> project) {
        this.project = project;
    }

    @Override
    public String getIconFileName() {
        return "document.png";
    }

    @Override
    public String getDisplayName() {
        return "Git Directory Strategy";
    }

    @Override
    public String getUrlName() {
        return "git-dir-strategy";
    }

    public AbstractProject<?, ?> getProject() {
        return project;
    }

    public String getProjectName() {
        return project.getName();
    }

    public String getProjectUrl() {
        return project.getUrl();
    }

    public Run<?, ?> getLastBuild() {
        return project.getLastBuild();
    }

    public boolean isBuildable() {
        return project.isBuildable();
    }
}

