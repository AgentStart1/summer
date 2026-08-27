package com.example.summerapp.testing

import com.example.summerapp.ui.host.AppDispatchers
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope

class FailOnUseDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable): Nothing {
        error(
            "Host scheduled work on its forbidden base dispatcher. " +
                "Use the injected Default or IO dispatcher explicitly."
        )
    }
}

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
        scope = CoroutineScope(hostJob + FailOnUseDispatcher()),
        dispatchers = AppDispatchers(default = defaultDispatcher, io = ioDispatcher),
        defaultDispatcher = defaultDispatcher,
        ioDispatcher = ioDispatcher,
        job = hostJob,
    )
}
