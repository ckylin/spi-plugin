package com.github.kylinc.spi;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.gradle.api.DefaultTask;

public class SpiTask extends DefaultTask {
  //unify log Prefix
  final String logPrefix = "> buildSpi :";

  String spiEntryNamePrefix = "META-INF/service/";

  public void buildSpi(String buildPath,List<String> interfaces,String jarName){
    System.out.println(logPrefix+"start");
    File buildFile = new File(buildPath);
    List<URL> urls = new ArrayList<>();
    File[] files = buildFile.listFiles(path -> {
      String fileName = path.getName();
      if(fileName.endsWith(".jar")){
        return true;
      }
      return false;
    });

    for(File file:files){
      try {
        urls.add(file.toURI().toURL());
        if(file.getName().equals(jarName+".jar")){
          throw new RuntimeException("build error,jar name ："+jarName+" is exists");
        }
      } catch (MalformedURLException e) {
        e.printStackTrace();
      }
    }

    URLClassLoader urlClassLoader = new URLClassLoader(urls.toArray(new URL[0]));

    Map<Class,List<Class>> interfaceMap = loadInterface(urlClassLoader,interfaces);

    try(JarOutputStream out = new JarOutputStream(new FileOutputStream(buildPath+"/"+jarName,true));) {

      //遍历jar包，把所有的class复制到到一个jar包中
      Arrays.asList(files).forEach(file -> {
        try {
          JarFile jarFile = new JarFile(file);

          Enumeration<JarEntry> entries = jarFile.entries();

          while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();

            System.out.println(logPrefix + jarEntry.getName());

            try {
              out.putNextEntry(jarEntry);

              String entryName = "jar:file:"+file.getPath()+"!/"+jarEntry.getName();

              InputStream in = new URL(entryName).openStream();

              byte[] buffer = new byte[100];

              int num = 0;
              while ((num = in.read(buffer)) > 0) {
                if (num == 100)
                  out.write(buffer);
                else {
                  byte[] bytes = new byte[num];
                  for (int i = 0; i < num; i++) {
                    bytes[i] = buffer[i];
                  }
                  out.write(bytes);
                }
              }
            } catch (IOException e) {
              e.printStackTrace();
            }

            try {
              if (jarEntry.getName().endsWith(".class")) {
                String clazzName = jarEntry.getName().replace(".class", "").replaceAll("/", ".");
                Class clazz = urlClassLoader
                    .loadClass(clazzName);

                interfaceMap.forEach((aClass, classes) -> {
                  Class[] itfs = clazz.getInterfaces();
                  for (Class itf : itfs) {
                    if (itf == aClass) {
                      classes.add(clazz);
                      break;
                    }
                  }
                });

              }
            } catch (ClassNotFoundException e) {
              e.printStackTrace();
            }
          }

          File spiServicePath = new File(buildPath + "/" + spiEntryNamePrefix);

          addSpiToJar(interfaceMap,out,spiServicePath);


        } catch (IOException e) {
          e.printStackTrace();
        }
      });

    }catch (Exception e){
      e.printStackTrace();
    }

    System.out.println(logPrefix+"end");

  }

  /**
   * load interface from classloader
   * @param urlClassLoader
   * @param interfaces
   * @return interfaceMap
   */
  public Map<Class,List<Class>> loadInterface(ClassLoader urlClassLoader,List<String> interfaces){
    Map<Class,List<Class>> interfaceMap = new HashMap<>();

    interfaces.forEach(itf ->{
      try {
        Class clazz = urlClassLoader.loadClass(itf);
        if(clazz.isInterface())
          interfaceMap.put(clazz,new ArrayList());
        else
          throw new RuntimeException("extension.interfaces must be java interface");
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }

    });

    return interfaceMap;
  }

  /**
   * 添加spi 的service到jar包中
   * @param interfaceMap
   * @param out
   * @param spiServicePath
   */
  public void addSpiToJar(Map<Class,List<Class>> interfaceMap,JarOutputStream out,File spiServicePath){
    spiServicePath.mkdirs();
    interfaceMap.forEach(((aClass, classes) -> {
      File spiFile = new File(spiServicePath.getPath() + "/" + aClass.getName());

      try (BufferedWriter bw = new BufferedWriter(new FileWriter(spiFile));) {
        classes.forEach(clazz -> {
          try {
            bw.write(clazz.getName());
          } catch (IOException e) {
            e.printStackTrace();
          }
        });

        bw.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }

      JarEntry spiEntry = new JarEntry(spiEntryNamePrefix + aClass.getName());

      try {
        System.out.println(logPrefix + spiEntryNamePrefix);
        out.putNextEntry(new JarEntry(spiEntryNamePrefix));
        System.out.println(logPrefix + spiEntry.getName());
        out.putNextEntry(spiEntry);

        FileInputStream in = new FileInputStream(spiFile);
        byte[] buffer = new byte[100];

        int num = 0;
        while ((num = in.read(buffer)) > 0) {
          if (num == 100) {
            out.write(buffer);
          } else {
            byte[] bytes = new byte[num];
            for (int i = 0; i < num; i++) {
              bytes[i] = buffer[i];
            }
            out.write(bytes);
          }
        }
        out.flush();
      } catch (Exception e) {
        e.printStackTrace();
      }

    }));
  }
}
