package org.wallentines.gradle.packserver;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPlugin;

public class PackUploaderPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getPlugins().apply(JavaPlugin.class);
        ExtensionContainer extensions = target.getExtensions();
        extensions.create("packUploader", PackUploaderExtension.class);
    }
}
