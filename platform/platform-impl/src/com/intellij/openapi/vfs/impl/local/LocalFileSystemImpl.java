/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.Consumer;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class LocalFileSystemImpl extends LocalFileSystemBase implements ApplicationComponent {
  private final Object myLock = new Object();
  private final List<WatchRequestImpl> myRootsToWatch = new ArrayList<WatchRequestImpl>();
  private TreeNode myNormalizedTree = null;
  private final FileWatcher myWatcher;

  private static class WatchRequestImpl implements WatchRequest {
    private final String myRootPath;
    private final boolean myToWatchRecursively;
    private String myFSRootPath;
    private boolean myDominated;

    public WatchRequestImpl(String rootPath, final boolean toWatchRecursively) {
      final int index = rootPath.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (index >= 0) rootPath = rootPath.substring(0, index);

      File rootFile = new File(FileUtil.toSystemDependentName(rootPath));
      if (index > 0 || !rootFile.isDirectory()) {
        rootFile = rootFile.getParentFile();
        assert rootFile != null : rootPath;
      }

      myFSRootPath = rootFile.getAbsolutePath();
      myRootPath = FileUtil.toSystemIndependentName(myFSRootPath);
      myToWatchRecursively = toWatchRecursively;
    }

    @Override
    @NotNull
    public String getRootPath() {
      return myRootPath;
    }

    /** @deprecated implementation details (to remove in IDEA 13) */
    @Override
    @NotNull
    public String getFileSystemRootPath() {
      return myFSRootPath;
    }

    @Override
    public boolean isToWatchRecursively() {
      return myToWatchRecursively;
    }

    /** @deprecated implementation details (to remove in IDEA 13) */
    @Override
    public boolean dominates(@NotNull WatchRequest other) {
      return LocalFileSystemImpl.dominates(this, (WatchRequestImpl)other);
    }

    @Override
    public String toString() {
      return myRootPath;
    }
  }

  private static class TreeNode {
    private WatchRequestImpl watchRequest = null;
    private Map<String, TreeNode> nodes = new HashMap<String, TreeNode>();
  }

  public LocalFileSystemImpl() {
    myWatcher = FileWatcher.getInstance();
    if (myWatcher.isOperational()) {
      new StoreRefreshStatusThread().start();
    }
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "LocalFileSystem";
  }

  @TestOnly
  public void cleanupForNextTest(Set<VirtualFile> survivors) throws IOException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
    ((PersistentFS)ManagingFS.getInstance()).clearIdCache();

    for (VirtualFile root : ManagingFS.getInstance().getRoots(this)) {
      if (root instanceof VirtualDirectoryImpl) {
        ((VirtualDirectoryImpl)root).cleanupCachedChildren(survivors);
      }
    }

    myRootsToWatch.clear();
  }

  private WatchRequestImpl[] normalizeRootsForRefresh() {
    final List<WatchRequestImpl> result = new ArrayList<WatchRequestImpl>();

    // no need to call for a read action here since we're only called with it on hands already
    synchronized (myLock) {
      TreeNode rootNode = new TreeNode();
      for (WatchRequestImpl request : myRootsToWatch) {
        String rootPath = request.getRootPath();
        TreeNode currentNode = rootNode;
        MainLoop:
        for (String subPath : rootPath.split("/")) {
          if (!SystemInfo.isFileSystemCaseSensitive) {
            subPath = subPath.toLowerCase();
          }
          TreeNode nextNode = currentNode.nodes.get(subPath);
          if (nextNode != null) {
            currentNode = nextNode;
            if (currentNode.watchRequest != null && currentNode.watchRequest.isToWatchRecursively()) {
              // a parent path of this request is already being watched recursively - do not need to add this one
              request.myDominated = true;
              break MainLoop;
            }
          }
          else {
            TreeNode newNode = new TreeNode();
            currentNode.nodes.put(subPath, newNode);
            currentNode = newNode;
          }
        }
        if (currentNode.watchRequest == null) {
          currentNode.watchRequest = request;
        }
        else {
          // we already have a watchRequest configured - select the better of the two
          if (!currentNode.watchRequest.isToWatchRecursively()) {
            currentNode.watchRequest.myDominated = true;
            currentNode.watchRequest = request;
          }
          else {
            request.myDominated = true;
          }
        }

        if (currentNode.watchRequest.isToWatchRecursively() && currentNode.nodes.size() > 0) {
          // since we are watching this node recursively, we can remove it's children
          visitTree(currentNode, new Consumer<TreeNode>() {
            @Override
            public void consume(final TreeNode node) {
              if (node.watchRequest != null) {
                node.watchRequest.myDominated = true;
              }
            }
          });
          currentNode.nodes.clear();
        }
      }

      visitTree(rootNode, new Consumer<TreeNode>() {
        @Override
        public void consume(final TreeNode node) {
          if (node.watchRequest != null) {
            result.add(node.watchRequest);
          }
        }
      });
      myNormalizedTree = rootNode;
    }

    return result.toArray(new WatchRequestImpl[result.size()]);
  }

  private static void visitTree(TreeNode rootNode, Consumer<TreeNode> consumer) {
    for (TreeNode node : rootNode.nodes.values()) {
      consumer.consume(node);
      visitTree(node, consumer);
    }
  }

  private boolean isAlreadyWatched(final WatchRequestImpl request) {
    if (myNormalizedTree == null) {
      normalizeRootsForRefresh();
    }

    String rootPath = request.getRootPath();
    TreeNode currentNode = myNormalizedTree;
    for (String subPath : rootPath.split("/")) {
      if (!SystemInfo.isFileSystemCaseSensitive) {
        subPath = subPath.toLowerCase();
      }
      TreeNode nextNode = currentNode.nodes.get(subPath);
      if (nextNode == null) {
        return false;
      }
      currentNode = nextNode;
      if (currentNode.watchRequest != null && currentNode.watchRequest.isToWatchRecursively()) {
        return true;
      }
    }
    // if we reach here it means that the exact path is already present in the graph -
    // then this request is assumed to be present only if it is not being watched recursively
    return !request.isToWatchRecursively() && currentNode.watchRequest != null;
  }

  private static boolean dominates(final WatchRequestImpl request, final WatchRequestImpl other) {
    if (request.myToWatchRecursively) {
      return other.myRootPath.startsWith(request.myRootPath);
    }

    return !other.myToWatchRecursively && request.myRootPath.equals(other.myRootPath);
  }

  private void storeRefreshStatusToFiles() {
    if (myWatcher.isOperational()) {
      // TODO: different ways to mark dirty for all these cases
      markPathsDirty(myWatcher.getDirtyPaths());
      markFlatDirsDirty(myWatcher.getDirtyDirs());
      markRecursiveDirsDirty(myWatcher.getDirtyRecursivePaths());
    }
  }

  private void markPathsDirty(final List<String> dirtyFiles) {
    for (String dirtyFile : dirtyFiles) {
      String path = dirtyFile.replace(File.separatorChar, '/');
      VirtualFile file = findFileByPathIfCached(path);
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markDirty();
      }
    }
  }

  private void markFlatDirsDirty(final List<String> dirtyFiles) {
    for (String dirtyFile : dirtyFiles) {
      String path = dirtyFile.replace(File.separatorChar, '/');
      VirtualFile file = findFileByPathIfCached(path);
      if (file instanceof NewVirtualFile) {
        final NewVirtualFile nvf = (NewVirtualFile)file;
        nvf.markDirty();
        for (VirtualFile child : nvf.getCachedChildren()) {
          ((NewVirtualFile)child).markDirty();
        }
      }
    }
  }

  private void markRecursiveDirsDirty(final List<String> dirtyFiles) {
    for (String dirtyFile : dirtyFiles) {
      String path = dirtyFile.replace(File.separatorChar, '/');
      VirtualFile file = findFileByPathIfCached(path);
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markDirtyRecursively();
      }
    }
  }

  public void markSuspiciousFilesDirty(List<VirtualFile> files) {
    storeRefreshStatusToFiles();

    if (myWatcher.isOperational()) {
      for (String root : myWatcher.getManualWatchRoots()) {
        final VirtualFile suspiciousRoot = findFileByPathIfCached(root);
        if (suspiciousRoot != null) {
          ((NewVirtualFile)suspiciousRoot).markDirtyRecursively();
        }
      }
    }
    else {
      for (VirtualFile file : files) {
        if (file.getFileSystem() == this) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
      }
    }
  }

  private void setUpFileWatcher() {
    final Application application = ApplicationManager.getApplication();
    if (application.isDisposeInProgress() || !myWatcher.isOperational()) return;

    application.runReadAction(new Runnable() {
      public void run() {
        synchronized (myLock) {
          final WatchRequestImpl[] watchRequests = normalizeRootsForRefresh();
          final List<String> myRecursiveRoots = new ArrayList<String>();
          final List<String> myFlatRoots = new ArrayList<String>();

          for (WatchRequestImpl watchRequest : watchRequests) {
            if (watchRequest.isToWatchRecursively()) {
              myRecursiveRoots.add(watchRequest.myFSRootPath);
            }
            else {
              myFlatRoots.add(watchRequest.myFSRootPath);
            }
          }

          myWatcher.setWatchRoots(myRecursiveRoots, myFlatRoots);
        }
      }
    });
  }

  private class StoreRefreshStatusThread extends Thread {
    private static final long PERIOD = 1000;

    public StoreRefreshStatusThread() {
      super(StoreRefreshStatusThread.class.getSimpleName());
      setPriority(MIN_PRIORITY);
      setDaemon(true);
    }

    @Override
    public void run() {
      while (true) {
        final Application application = ApplicationManager.getApplication();
        if (application == null || application.isDisposed()) break;
        
        storeRefreshStatusToFiles();
        TimeoutUtil.sleep(PERIOD);
      }
    }
  }

  @Override
  @NotNull
  public Set<WatchRequest> addRootsToWatch(@NotNull final Collection<String> rootPaths, final boolean watchRecursively) {
    if (rootPaths.isEmpty() || !myWatcher.isOperational()) {
      return Collections.emptySet();
    }
    else if (watchRecursively) {
      return replaceWatchedRoots(Collections.<WatchRequest>emptySet(), rootPaths, null);
    }
    else {
      return replaceWatchedRoots(Collections.<WatchRequest>emptySet(), null, rootPaths);
    }
  }

  @Override
  public void removeWatchedRoots(@NotNull final Collection<WatchRequest> watchRequests) {
    if (watchRequests.isEmpty()) return;

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (myLock) {
          final boolean update = doRemoveWatchedRoots(watchRequests);
          if (update) {
            myNormalizedTree = null;
            setUpFileWatcher();
          }
        }
      }
    });
  }

  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull final Collection<WatchRequest> watchRequests,
                                               @Nullable final Collection<String> _recursiveRoots,
                                               @Nullable final Collection<String> _flatRoots) {
    final Collection<String> recursiveRoots = _recursiveRoots != null ? _recursiveRoots : Collections.<String>emptyList();
    final Collection<String> flatRoots = _flatRoots != null ? _flatRoots : Collections.<String>emptyList();

    if (recursiveRoots.isEmpty() && flatRoots.isEmpty() || !myWatcher.isOperational()) {
      removeWatchedRoots(watchRequests);
      return Collections.emptySet();
    }

    final Set<WatchRequest> result = new HashSet<WatchRequest>();
    final Set<VirtualFile> filesToSync = new HashSet<VirtualFile>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (myLock) {
          final boolean update = doAddRootsToWatch(recursiveRoots, flatRoots, result, filesToSync) ||
                                 doRemoveWatchedRoots(watchRequests);
          if (update) {
            myNormalizedTree = null;
            setUpFileWatcher();
          }
        }
      }
    });

    syncFiles(filesToSync);

    return result;
  }

  private boolean doAddRootsToWatch(@NotNull final Collection<String> recursiveRoots,
                                    @NotNull final Collection<String> flatRoots,
                                    @NotNull final Set<WatchRequest> results,
                                    @NotNull final Set<VirtualFile> filesToSync) {
    boolean update = false;

    for (String root : recursiveRoots) {
      final WatchRequestImpl request = new WatchRequestImpl(root, true);
      final boolean alreadyWatched = isAlreadyWatched(request);

      request.myDominated = alreadyWatched;
      myRootsToWatch.add(request);
      results.add(request);

      update |= !alreadyWatched;
    }

    for (String root : flatRoots) {
      final WatchRequestImpl request = new WatchRequestImpl(root, false);
      final boolean alreadyWatched = isAlreadyWatched(request);

      if (!alreadyWatched) {
        final VirtualFile existingFile = findFileByPathIfCached(root);
        if (existingFile != null && existingFile.isDirectory() && existingFile instanceof NewVirtualFile) {
          filesToSync.addAll(((NewVirtualFile)existingFile).getCachedChildren());
        }
      }

      request.myDominated = alreadyWatched;
      myRootsToWatch.add(request);
      results.add(request);

      update |= !alreadyWatched;
    }

    return update;
  }

  private void syncFiles(@NotNull final Set<VirtualFile> filesToSync) {
    if (filesToSync.isEmpty() || ApplicationManager.getApplication().isUnitTestMode()) return;

    for (VirtualFile file : filesToSync) {
      if (file instanceof NewVirtualFile && file.getFileSystem() instanceof LocalFileSystem) {
        ((NewVirtualFile)file).markDirtyRecursively();
      }
    }

    refreshFiles(filesToSync, true, false, null);
  }

  private boolean doRemoveWatchedRoots(@NotNull final Collection<WatchRequest> watchRequests) {
    boolean update = false;

    for (WatchRequest watchRequest : watchRequests) {
      final boolean wasWatched = myRootsToWatch.remove((WatchRequestImpl)watchRequest) && !((WatchRequestImpl)watchRequest).myDominated;
      update |= wasWatched;
    }

    return update;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    Runnable heavyRefresh = new Runnable() {
      @Override
      public void run() {
        for (VirtualFile root : ManagingFS.getInstance().getRoots(LocalFileSystemImpl.this)) {
          ((NewVirtualFile)root).markDirtyRecursively();
        }

        refresh(asynchronous);
      }
    };

    if (asynchronous && myWatcher.isOperational()) {
      RefreshQueue.getInstance().refresh(true, true, heavyRefresh, ManagingFS.getInstance().getRoots(this));
    }
    else {
      heavyRefresh.run();
    }
  }

  @Override
  public FileAttributes getAttributes(@NotNull final VirtualFile file) {
    return FileSystemUtil.getAttributes(FileUtil.toSystemDependentName(file.getPath()));
  }

  @NonNls
  public String toString() {
    return "LocalFileSystem";
  }
}
