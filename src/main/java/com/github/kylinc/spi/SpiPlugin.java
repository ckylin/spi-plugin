package com.github.kylinc.spi;

import java.util.Arrays;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class SpiPlugin implements Plugin<Project> {


  @Override
  public void apply(Project project) {
    SpiPluginExtension extension= project.getExtensions().create("buildSpiExtension",SpiPluginExtension.class);


    SpiTask spiTask = project.getTasks().create("buildSpi", SpiTask.class);

    Task buildTask = project.getTasks().getByName("build");

    String buildPath = project.getBuildDir().getPath()+"/libs";

    spiTask.dependsOn(buildTask);

    spiTask.doFirst(task -> {
      spiTask.buildSpi(buildPath,Arrays.asList(extension.interfaces));
    });

  }
}
