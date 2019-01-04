package com.github.kylinc.spi;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
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

  public void buildSpi(String buildPath,List<String> interfaces){
    System.out.println(logPrefix+"start");
    File buildFile = new File(buildPath);
    File[] files = buildFile.listFiles(path -> {
      String fileName = path.getName();
      if(fileName.endsWith(".jar")){
        return true;
      }
      return false;
    });

    Arrays.asList(files).forEach(jar -> {
      File newJar = new File(jar.getPath()+".new");
      try(JarOutputStream out = new JarOutputStream(new FileOutputStream(newJar));JarFile jarFile = new JarFile(jar);){

        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{jar.toURI().toURL()});

        Map<Class,List<Class>> interfaceMap = loadInterface(urlClassLoader,interfaces);


        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
          JarEntry jarEntry = entries.nextElement();

          System.out.println(logPrefix + jarEntry.getName());

          out.putNextEntry(jarEntry);

          String entryName = "jar:file:"+jar.getPath()+"!/"+jarEntry.getName();

          JarURLConnection jarURLConnection =(JarURLConnection) new URL(entryName).openConnection();
          jarURLConnection.setUseCaches(false);

          try(InputStream in = jarURLConnection.getInputStream();) {

            byte[] buffer = new byte[1000];

            int num;
            while ((num = in.read(buffer)) > 0) {
              if (num == 1000) {
                out.write(buffer);

              } else {
                byte[] bytes = new byte[num];
                for (int i = 0; i < num; i++) {
                  bytes[i] = buffer[i];
                }
                out.write(bytes);
              }
            }
          }catch (Exception e){
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


        addSpiToJar(interfaceMap, out);
        if(jar.delete()){
          newJar.renameTo(jar);
        }

      } catch (Exception e) {
        e.printStackTrace();
      }
    });

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
   */
  public void addSpiToJar(Map<Class,List<Class>> interfaceMap,JarOutputStream out){
    interfaceMap.forEach(((aClass, classes) -> {

      JarEntry spiEntry = new JarEntry(spiEntryNamePrefix + aClass.getName());

      try {
        System.out.println(logPrefix + spiEntryNamePrefix);
        out.putNextEntry(new JarEntry(spiEntryNamePrefix));
        System.out.println(logPrefix + spiEntry.getName());
        out.putNextEntry(spiEntry);

        classes.forEach(clazz ->{
          try {
            out.write(clazz.getName().getBytes());
            out.write("\n".getBytes());
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
        out.flush();
      } catch (Exception e) {
        e.printStackTrace();
      }

    }));
  }
}
