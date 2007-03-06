// ============================================================================
//
// Talend Community Edition
//
// Copyright (C) 2006 Talend - www.talend.com
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
//
// ============================================================================
package org.talend.designer.codegen.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.emf.codegen.CodeGenPlugin;
import org.eclipse.emf.codegen.jet.JETCompiler;
import org.eclipse.emf.codegen.jet.JETEmitter;
import org.eclipse.emf.codegen.jet.JETException;
import org.eclipse.emf.codegen.util.CodeGenUtil;
import org.eclipse.emf.common.CommonPlugin;
import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.Monitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.framework.Bundle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * DOC mhirt class global comment. Detailled comment <br/>
 * 
 * $Id$
 * 
 */
public class TalendJetEmitter extends JETEmitter {
    private String templateName;
    private String templateLanguage;
    private String codePart;
    
    /**
     * DOC mhirt TalendJetEmitter constructor comment.
     * 
     * @param arg0
     * @param arg1
     */
    public TalendJetEmitter(String arg0, ClassLoader arg1, String templateName, String templateLanguage, String codePart) {
        super(arg0, arg1);
        this.templateName = templateName;
        this.templateLanguage = templateLanguage;
        this.codePart = codePart;
    }

    /**
     * Compiles the template to {@link #setMethod set} the method will be invoked to generate template results.
     * 
     * @param progressMonitor the progress monitor for tracking progress.
     */
    @Override
    public void initialize(IProgressMonitor progressMonitor) throws JETException {
        initialize(BasicMonitor.toMonitor(progressMonitor));
    }

    /**
     * Compiles the template to {@link #setMethod set} the method will be invoked to generate template results.
     * 
     * @param progressMonitor the progress monitor for tracking progress.
     */
    @Override
    public void initialize(Monitor progressMonitor) throws JETException {
        if (EMFPlugin.IS_ECLIPSE_RUNNING) {
            TalendEclipseHelper.initialize(progressMonitor, this, templateName, templateLanguage, codePart);
        }
    }

    /**
     * . 
     */
    private static class TalendEclipseHelper
    {
      public static void initialize(Monitor monitor, TalendJetEmitter jetEmitter, String templateName, String templateLanguage, String codePart) throws JETException
      {
        IProgressMonitor progressMonitor = BasicMonitor.toIProgressMonitor(monitor);
        progressMonitor.beginTask("", 10);
        progressMonitor.subTask(CodeGenPlugin.getPlugin().getString("_UI_GeneratingJETEmitterFor_message", new Object [] { jetEmitter.templateURI }));
    
        try
        {
          final JETCompiler jetCompiler = 
            jetEmitter.templateURIPath == null ? 
              new MyBaseJETCompiler(jetEmitter.templateURI, jetEmitter.encoding, jetEmitter.classLoader) :
              new MyBaseJETCompiler(jetEmitter.templateURIPath, jetEmitter.templateURI, jetEmitter.encoding, jetEmitter.classLoader);
    
          progressMonitor.subTask
            (CodeGenPlugin.getPlugin().getString("_UI_JETParsing_message", new Object [] { jetCompiler.getResolvedTemplateURI() }));
          jetCompiler.parse();
          jetCompiler.getSkeleton().setClassName(templateName + codePart + templateLanguage);
          progressMonitor.worked(1);
    
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          jetCompiler.generate(outputStream);
          final InputStream contents = new ByteArrayInputStream(outputStream.toByteArray());
    
          final IWorkspace workspace = ResourcesPlugin.getWorkspace();
          IJavaModel javaModel= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
          if (!javaModel.isOpen())
          {
            javaModel.open(new SubProgressMonitor(progressMonitor, 1));
          }
          else
          {
            progressMonitor.worked(1);
          }
    
          final IProject project = workspace.getRoot().getProject(jetEmitter.getProjectName());
          progressMonitor.subTask
            (CodeGenPlugin.getPlugin().getString("_UI_JETPreparingProject_message", new Object [] { project.getName() }));
    
          IJavaProject javaProject;
          if (!project.exists())
          {
            progressMonitor.subTask("JET creating project " + project.getName());
            project.create(new SubProgressMonitor(progressMonitor, 1));
            progressMonitor.subTask
              (CodeGenPlugin.getPlugin().getString("_UI_JETCreatingProject_message", new Object [] { project.getName() }));
            IProjectDescription description = workspace.newProjectDescription(project.getName());
            description.setNatureIds(new String [] { JavaCore.NATURE_ID });
            description.setLocation(null);
            project.open(new SubProgressMonitor(progressMonitor, 1));
            project.setDescription(description, new SubProgressMonitor(progressMonitor, 1));
          }
          else
          {
            project.open(new SubProgressMonitor(progressMonitor, 5));
            IProjectDescription description = project.getDescription();
            description.setNatureIds(new String [] { JavaCore.NATURE_ID });
            project.setDescription(description, new SubProgressMonitor(progressMonitor, 1));
          }
    
          javaProject = JavaCore.create(project);
    
          progressMonitor.subTask
            (CodeGenPlugin.getPlugin().getString("_UI_JETInitializingProject_message", new Object [] { project.getName() }));
          IClasspathEntry classpathEntry = 
            JavaCore.newSourceEntry(new Path("/" + project.getName() + "/src"));

          IClasspathEntry jreClasspathEntry = JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER"));

          List classpath = new ArrayList();
          classpath.add(classpathEntry);
          classpath.add(jreClasspathEntry);
          classpath.addAll(jetEmitter.classpathEntries);
    
          IFolder sourceFolder = project.getFolder(new Path("src"));
          if (!sourceFolder.exists())
          {
            sourceFolder.create(false, true, new SubProgressMonitor(progressMonitor, 1));
          }
          IFolder runtimeFolder = project.getFolder(new Path("runtime"));
          if (!runtimeFolder.exists())
          {
            runtimeFolder.create(false, true, new SubProgressMonitor(progressMonitor, 1));
          }
    
          IClasspathEntry [] classpathEntryArray = (IClasspathEntry[])classpath.toArray(new IClasspathEntry [classpath.size()]);
    
          javaProject.setRawClasspath(classpathEntryArray, new SubProgressMonitor(progressMonitor, 1));
    
          javaProject.setOutputLocation(new Path("/" + project.getName() + "/runtime"), new SubProgressMonitor(progressMonitor, 1));
    
          javaProject.close();
    
          progressMonitor.subTask
            (CodeGenPlugin.getPlugin().getString("_UI_JETOpeningJavaProject_message", new Object [] { project.getName() }));
          javaProject.open(new SubProgressMonitor(progressMonitor, 1));
    
          IPackageFragmentRoot [] packageFragmentRoots = javaProject.getPackageFragmentRoots();
          IPackageFragmentRoot sourcePackageFragmentRoot = null;
          for (int j = 0; j < packageFragmentRoots.length; ++j)
          {
            IPackageFragmentRoot packageFragmentRoot = packageFragmentRoots[j];
            if (packageFragmentRoot.getKind() == IPackageFragmentRoot.K_SOURCE)
            {
              sourcePackageFragmentRoot = packageFragmentRoot;
              break;
            }
          }
    
          String packageName = jetCompiler.getSkeleton().getPackageName();
          StringTokenizer stringTokenizer = new StringTokenizer(packageName, ".");
          IProgressMonitor subProgressMonitor = new SubProgressMonitor(progressMonitor, 1);
          subProgressMonitor.beginTask("", stringTokenizer.countTokens() + 4);
          subProgressMonitor.subTask(CodeGenPlugin.getPlugin().getString("_UI_CreateTargetFile_message"));
          IContainer sourceContainer = (IContainer)sourcePackageFragmentRoot.getCorrespondingResource();
          while (stringTokenizer.hasMoreElements())
          {
            String folderName = stringTokenizer.nextToken();
            sourceContainer = sourceContainer.getFolder(new Path(folderName));
            if (!sourceContainer.exists())
            {
              ((IFolder)sourceContainer).create(false, true, new SubProgressMonitor(subProgressMonitor, 1));
            }
          }
          IFile targetFile = sourceContainer.getFile(new Path(jetCompiler.getSkeleton().getClassName() + ".java"));
          if (!targetFile.exists())
          {
            subProgressMonitor.subTask
              (CodeGenPlugin.getPlugin().getString("_UI_JETCreating_message", new Object [] { targetFile.getFullPath() }));
            targetFile.create(contents, true, new SubProgressMonitor(subProgressMonitor, 1));
          }
          else
          {
            subProgressMonitor.subTask
              (CodeGenPlugin.getPlugin().getString("_UI_JETUpdating_message", new Object [] { targetFile.getFullPath() }));
            targetFile.setContents(contents, true, true, new SubProgressMonitor(subProgressMonitor, 1));
          }
    
          subProgressMonitor.subTask
            (CodeGenPlugin.getPlugin().getString("_UI_JETBuilding_message", new Object [] { project.getName() }));
          project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new SubProgressMonitor(subProgressMonitor, 1));
    
          IMarker [] markers = targetFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
          boolean errors = false;
          for (int i = 0; i < markers.length; ++i)
          {
            IMarker marker = markers[i];
            if (marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_ERROR)
            {
              errors = true;
              subProgressMonitor.subTask
                (marker.getAttribute(IMarker.MESSAGE) + " : " + 
                   (CodeGenPlugin.getPlugin().getString
                     ("jet.mark.file.line", 
                      new Object []
                      {
                        targetFile.getLocation(), 
                        marker.getAttribute(IMarker.LINE_NUMBER)
                      })));
            }
          }
    
          if (!errors)
          {
            subProgressMonitor.subTask
              (CodeGenPlugin.getPlugin().getString
                 ("_UI_JETLoadingClass_message", new Object [] { jetCompiler.getSkeleton().getClassName() + ".class" }));
    
            // Construct a proper URL for relative lookup.
            //
            URL url = new File(project.getLocation() + "/" + javaProject.getOutputLocation().removeFirstSegments(1) + "/").toURL();
            URLClassLoader theClassLoader = new URLClassLoader(new URL [] { url }, jetEmitter.classLoader);
            Class theClass = 
              theClassLoader.loadClass
                ((packageName.length() == 0 ? "" : packageName + ".") + jetCompiler.getSkeleton().getClassName());
            String methodName = jetCompiler.getSkeleton().getMethodName();
            Method [] methods = theClass.getDeclaredMethods();
            for (int i = 0; i < methods.length; ++i)
            {
              if (methods[i].getName().equals(methodName))
              {
                jetEmitter.setMethod(methods[i]);
                break;
              }
            }
          }
    
          subProgressMonitor.done();
        }
        catch (CoreException exception)
        {
          throw new JETException(exception);
        }
        catch (Exception exception)
        {
          throw new JETException(exception);
        }
        finally
        {
          progressMonitor.done();
        }
      }
      
      public static void addVariable(JETEmitter jetEmitter, String variableName, String pluginID) throws JETException
      {
        Bundle bundle = Platform.getBundle(pluginID);
        URL classpathURL = Platform.inDevelopmentMode() ? bundle.getEntry(".classpath") : null;
        if (classpathURL != null)
        {
          DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
          documentBuilderFactory.setNamespaceAware(true);
          documentBuilderFactory.setValidating(false);
          try
          {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(new InputSource(classpathURL.toString()));
            for (Node child = document.getDocumentElement().getFirstChild(); child != null; child = child.getNextSibling())
            {
              if (child.getNodeType() == Node.ELEMENT_NODE)
              {
                Element classpathEntryElement = (Element)child;
                if ("classpathentry".equals(classpathEntryElement.getNodeName()) &&
                    "output".equals(classpathEntryElement.getAttribute("kind")))
                {
                  URI uri = URI.createURI(classpathEntryElement.getAttribute("path")).resolve(URI.createURI(classpathURL.toString()));
                  IWorkspace workspace = ResourcesPlugin.getWorkspace();
                  IProject project = workspace.getRoot().getProject(jetEmitter.getProjectName());
                  if (!project.exists())
                  {
                    project.create(new NullProgressMonitor());
                  }
                  if (!project.isOpen())
                  {
                    project.open(new NullProgressMonitor());
                  }
                  IFolder folder = project.getFolder("." + pluginID);
                  if (!folder.exists())
                  {
                    folder.createLink
                      (new Path(CommonPlugin.asLocalURI(uri).toFileString()).removeTrailingSeparator(),
                       IResource.ALLOW_MISSING_LOCAL, 
                       new NullProgressMonitor());
                  }
                  folder.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
                  IPath path = folder.getFullPath();
                  jetEmitter.getClasspathEntries().add(JavaCore.newLibraryEntry(path, null, null));
                  break;
                }
              }
            }
          }
          catch (Exception exception)
          {
            CodeGenPlugin.INSTANCE.log(exception);
          }
        }
        else
        {
          CodeGenUtil.EclipseUtil.addClasspathEntries(jetEmitter.getClasspathEntries(), variableName, pluginID);
        }
      }
    }

}
