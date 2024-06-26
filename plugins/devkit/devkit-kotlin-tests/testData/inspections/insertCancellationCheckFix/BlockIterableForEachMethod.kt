package inspections.insertCancellationCheckFix

import com.intellij.util.concurrency.annotations.RequiresReadLock
import inspections.cancellationCheckInLoops.Foo.doSomething

@RequiresReadLock
fun main(list: Iterable<String>) {
  list.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for<caret>Each</warning> {
      doSomething()
  }
}
