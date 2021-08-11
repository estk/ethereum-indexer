package com.rarible.protocol.nft.core.service.item

import com.rarible.ethereum.domain.EthUInt256
import com.rarible.protocol.contracts.external.KnownOrigin.KnownOrigin
import com.rarible.protocol.nft.core.model.ItemCreator
import com.rarible.protocol.nft.core.model.ItemId
import com.rarible.protocol.nft.core.model.KNOWN_ORIGIN_TOKEN
import com.rarible.protocol.nft.core.repository.ItemCreatorRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import scalether.domain.Address
import scalether.transaction.MonoTransactionSender
import java.math.BigInteger

@Service
class ItemCreatorService(
    sender: MonoTransactionSender,
    private val itemCreatorRepository: ItemCreatorRepository
) {
    private val knownOrigin = KnownOrigin(KNOWN_ORIGIN_TOKEN, sender)

    fun getCreator(itemId: ItemId): Mono<Address> {
        val (token, tokenId) = itemId
        if (token !in fetchingCollections) {
            return Mono.empty()
        }
        return itemCreatorRepository
            .findById(itemId)
            .map { it.creator }
            .switchIfEmpty {
                if (token == KNOWN_ORIGIN_TOKEN) {
                    fetchKnownOriginArtist(tokenId, itemId)
                } else {
                    Mono.empty()
                }
            }
    }

    private fun fetchKnownOriginArtist(tokenId: EthUInt256, itemId: ItemId): Mono<Address> {
        logger.info("fetchKnownOriginArtist")
        return knownOrigin.editionOfTokenId(tokenId.value)
            .flatMap { edition ->
                if (edition == BigInteger.ZERO) {
                    //edition not found. not existing item in known origin
                    Mono.empty()
                } else {
                    knownOrigin.detailsOfEdition(edition)
                        .flatMap { details ->
                            val creator = details._5()
                            itemCreatorRepository.save(ItemCreator(itemId, creator))
                                .then(
                                    Mono.just(creator)
                                )
                        }
                }
            }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ItemCreatorService::class.java)
        private val fetchingCollections = listOf(KNOWN_ORIGIN_TOKEN)
    }
}