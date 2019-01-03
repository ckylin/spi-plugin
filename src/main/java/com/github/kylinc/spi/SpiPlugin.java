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

    System.out.println("build path:"+buildPath);

    spiTask.doFirst(task -> {
      if(extension.jarName==null)
        extension.jarName = project.getName() + "-spi-" + project.getVersion()+".jar";
      else
        extension.jarName = extension.jarName+".jar";
      spiTask.buildSpi(buildPath,Arrays.asList(extension.interfaces),extension.jarName);
    });

  }
}
