package com.rarible.protocol.order.core.repository.auction

import com.rarible.core.apm.CaptureSpan
import com.rarible.core.apm.SpanType
import com.rarible.core.reduce.repository.ReduceEventRepository
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.protocol.order.core.misc.div
import com.rarible.protocol.order.core.model.*
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findAll
import org.springframework.data.mongodb.core.query.*
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@CaptureSpan(type = SpanType.DB)
@Component
class AuctionHistoryRepository(
    private val template: ReactiveMongoTemplate
) : ReduceEventRepository<AuctionReduceEvent, Long, Word> {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun save(logEvent: LogEvent): Mono<LogEvent> {
        return template.save(logEvent, COLLECTION)
    }

    fun findByType(type: AuctionHistoryType): Flux<LogEvent> {
        val query = Query(LogEvent::topic inValues type.topic)
        return template.find(query, COLLECTION)
    }

    fun searchActivity(filter: ActivityAuctionHistoryFilter, size: Int): Flux<LogEvent> {
        val hint = filter.hint
        val criteria = filter.getCriteria().and(LogEvent::status).isEqualTo(LogEventStatus.CONFIRMED)

        val query = Query(criteria).limit(size)

        if (hint != null) {
            query.withHint(hint)
        }
        return template.find(query.with(filter.sort), LogEvent::class.java, COLLECTION)
    }

    override fun getEvents(key: Word?, after: Long?): Flow<AuctionReduceEvent> {
        val criteria = Criteria()
            .run {
                key?.let { and(LogEvent::data / AuctionHistory::hash).isEqualTo(key) } ?: this
            }
            .run {
                after?.let { and(LogEvent::blockNumber).gt(it) } ?: this
            }

        val query = Query(criteria).with(LOG_SORT_ASC)
        return template
            .find(query, LogEvent::class.java, COLLECTION)
            .map { logEvent -> AuctionReduceEvent(logEvent) }
            .asFlow()
    }

    fun findAll(): Flux<LogEvent> {
        return template.findAll(COLLECTION)
    }

    companion object {
        const val COLLECTION = "auction_history"

        val LOG_SORT_ASC: Sort = Sort
            .by(
                "${LogEvent::data.name}.${AuctionHistory::hash.name}",
                LogEvent::blockNumber.name,
                LogEvent::logIndex.name,
                LogEvent::minorLogIndex.name
            )
    }
}
