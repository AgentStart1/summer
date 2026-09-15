package com.storytellerf.summer.testing

import com.storytellerf.summer.ui.host.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope

class HostTestEnvironment(
    val scope: CoroutineScope,
    val dispatchers: AppDispatchers,
    val defaultDispatcher: CoroutineDispatcher,
    val ioDispatcher: CoroutineDispatcher,
    private val job: Job,
) : AutoCloseable {
    override fun close() {
        job.cancel()
    }
}

fun TestScope.createHostTestEnvironment(
    scheduler: TestCoroutineScheduler = testScheduler,
): HostTestEnvironment {
    val defaultDispatcher = StandardTestDispatcher(scheduler, "host-default")
    val ioDispatcher = StandardTestDispatcher(scheduler, "host-io")
    val hostJob = SupervisorJob(coroutineContext[Job])
    return HostTestEnvironment(
        scope = CoroutineScope(hostJob + defaultDispatcher),
        dispatchers = AppDispatchers(default = defaultDispatcher, io = ioDispatcher),
        defaultDispatcher = defaultDispatcher,
        ioDispatcher = ioDispatcher,
        job = hostJob,
    )
}
