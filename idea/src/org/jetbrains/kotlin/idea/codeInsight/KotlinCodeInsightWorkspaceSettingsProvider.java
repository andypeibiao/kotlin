/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeInsight;

import com.intellij.application.options.editor.AutoImportOptionsProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class KotlinCodeInsightWorkspaceSettingsProvider implements AutoImportOptionsProvider {
    private final Project project;

    private JPanel myPanel;
    private JCheckBox myOptimizeImportsOnTheFly;
    private JCheckBox myAddUnambiguousImportsOnTheFly;

    public KotlinCodeInsightWorkspaceSettingsProvider(Project project) {
        this.project = project;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return myPanel;
    }

    private KotlinCodeInsightWorkspaceSettings settings() {
        return KotlinCodeInsightWorkspaceSettings.Companion.getInstance(project);
    }

    @Override
    public boolean isModified() {
        KotlinCodeInsightWorkspaceSettings settings = settings();
        return settings.optimizeImportsOnTheFly != myOptimizeImportsOnTheFly.isSelected()
               || settings.addUnambiguousImportsOnTheFly != myAddUnambiguousImportsOnTheFly.isSelected();
    }

    @Override
    public void apply() throws ConfigurationException {
        KotlinCodeInsightWorkspaceSettings settings = settings();

        settings.optimizeImportsOnTheFly = myOptimizeImportsOnTheFly.isSelected();
        settings.addUnambiguousImportsOnTheFly = myAddUnambiguousImportsOnTheFly.isSelected();
    }

    @Override
    public void reset() {
        KotlinCodeInsightWorkspaceSettings settings = settings();
        myOptimizeImportsOnTheFly.setSelected(settings.optimizeImportsOnTheFly);
        myAddUnambiguousImportsOnTheFly.setSelected(settings.addUnambiguousImportsOnTheFly);
    }
}
