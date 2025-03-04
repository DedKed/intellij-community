// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CancellableReadActionWithJobTest : CancellableReadActionTests() {

  @Test
  fun context() {
    currentJobTest { currentJob ->
      val application = ApplicationManager.getApplication()

      assertNull(ProgressManager.getGlobalProgressIndicator())
      application.assertReadAccessNotAllowed()

      val result = computeCancellable {
        val readJob = requireNotNull(Cancellation.currentJob())
        assertJobIsChildOf(job = readJob, parent = currentJob)
        assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertReadAccessAllowed()
        42
      }
      assertEquals(42, result)

      assertNull(ProgressManager.getGlobalProgressIndicator())
      application.assertReadAccessNotAllowed()
    }
  }

  @Test
  fun cancellation() {
    val job = Job()
    withJob(job) {
      assertThrows<CancellationException> {
        computeCancellable {
          testNoExceptions()
          job.cancel()
          testExceptions()
        }
      }
    }
  }

  @Test
  fun rethrow() {
    currentJobTest {
      testComputeCancellableRethrow()
    }
  }

  @Test
  fun `throws when a write is pending`() {
    currentJobTest {
      testThrowsIfPendingWrite()
    }
  }

  @Test
  fun `throws when a write is running`() {
    currentJobTest {
      testThrowsIfRunningWrite()
    }
  }

  @Test
  fun `does not throw when a write is requested during almost finished computation`() {
    currentJobTest {
      testDoesntThrowWhenAlmostFinished()
    }
  }

  @Test
  fun `throws when a write is requested during computation`() {
    currentJobTest {
      testThrowsOnWrite()
    }
  }

  @Test
  fun `throws inside non-cancellable read action when a write is requested during computation`() {
    currentJobTest {
      runReadAction {
        testThrowsOnWrite()
      }
    }
  }
}
