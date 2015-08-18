package gov.sbs.plugins;

import org.gradle.api.Project;
import org.gradle.api.Plugin;

public class VanderBlamePlugin implements Plugin<Project> {
    public void apply(Project target) {
        target.task("vanderBlameTask");
    }
}
