package com.storytellerf.summer.ui.feed

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.storytellerf.summer.data.DataRepository
import com.storytellerf.summer.ui.host.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn

@OptIn(ExperimentalCoroutinesApi::class)
class FeedHost(repository: DataRepository, scope: CoroutineScope, dispatchers: AppDispatchers) : AutoCloseable {
    private val hostScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]) + dispatchers.coordination)
    // Any affected table invalidates the generation, including account names and imported transactions.
    val items: Flow<PagingData<TimelineItem>> = repository.observeTimelineChanges().flatMapLatest {
        Pager(PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = false)) {
            FeedPagingSource(repository, dispatchers)
        }.flow
    }.flowOn(dispatchers.default).cachedIn(hostScope)

    override fun close() = hostScope.cancel()
}
