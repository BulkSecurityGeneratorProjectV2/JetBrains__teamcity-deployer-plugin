/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.deployer.agent;

import jetbrains.buildServer.RunBuildException;
import jetbrains.buildServer.agent.BuildFinishedStatus;
import jetbrains.buildServer.agent.BuildProcessAdapter;
import jetbrains.buildServer.agent.BuildProgressLogger;
import org.jetbrains.annotations.NotNull;

import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.atomic.AtomicReference;

public abstract class SyncBuildProcessAdapter extends BuildProcessAdapter {
  protected final BuildProgressLogger myLogger;
  private volatile boolean hasFinished;
  private volatile BuildFinishedStatus statusCode;
  private volatile boolean isInterrupted;
  private final AtomicReference<Thread> runningThread = new AtomicReference<>();


  public SyncBuildProcessAdapter(@NotNull final BuildProgressLogger logger) {
    myLogger = logger;
    hasFinished = false;
    statusCode = null;
  }


  @Override
  public void interrupt() {
    isInterrupted = true; // leave this logic
    Thread running = runningThread.get();
    if (running != null) {
      running.interrupt();
    }
  }

  @Override
  public boolean isInterrupted() {
    return isInterrupted;
  }

  @Override
  public boolean isFinished() {
    return hasFinished;
  }

  @NotNull
  @Override
  public BuildFinishedStatus waitFor() throws RunBuildException {
    while (!isInterrupted() && !hasFinished) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        throw new RunBuildException(e);
      }
    }
    return hasFinished ? statusCode : BuildFinishedStatus.INTERRUPTED;
  }

  @Override
  public void start() throws RunBuildException {
    try {
      runningThread.set(Thread.currentThread());
      statusCode = runProcess();
      hasFinished = true;
    } catch (UploadInterruptedException e) {
      hasFinished = false;
    } catch (InterruptedException | ClosedByInterruptException ie) {
      hasFinished = false;
      if (!isInterrupted) { // clears interrupted state if any left
        Thread.currentThread().interrupt();
      }
    } finally {
      runningThread.set(null);
    }
  }

  /**
   * @return true is process finished successfully
   */
  protected abstract BuildFinishedStatus runProcess() throws InterruptedException, ClosedByInterruptException;

  protected void checkIsInterrupted() throws UploadInterruptedException {
    if (isInterrupted()) throw new UploadInterruptedException();
  }

}
