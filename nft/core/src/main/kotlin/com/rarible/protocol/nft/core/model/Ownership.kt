package com.rarible.protocol.nft.core.model

import com.rarible.core.common.nowMillis
import com.rarible.core.entity.reducer.model.RevertableEntity
import com.rarible.ethereum.domain.EthUInt256
import org.springframework.data.annotation.AccessType
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import scalether.domain.Address
import java.time.Instant

@Document("ownership")
@CompoundIndexes(
    CompoundIndex(def = "{token: 1, tokenId: 1, owner: 1}", background = true, unique = true, sparse = true),
    CompoundIndex(def = "{owner: 1, date: 1, _id: 1}", background = true),
    CompoundIndex(def = "{token: 1, date: 1, _id: 1}", background = true)
)
data class Ownership(
    val token: Address,
    val tokenId: EthUInt256,
    val creators: List<Part> = emptyList(),
    val owner: Address,
    val value: EthUInt256,
    val lazyValue: EthUInt256 = EthUInt256.ZERO,
    val date: Instant,
    val pending: List<ItemTransfer>,
    val deleted: Boolean = false,
    override val events: List<OwnershipEvent> = emptyList()
) : RevertableEntity<OwnershipId, OwnershipEvent, Ownership> {

    @Transient
    private val _id: OwnershipId = OwnershipId(token, tokenId, owner)

    @get:Id
    @get:AccessType(AccessType.Type.PROPERTY)
    override var id: OwnershipId
        get() = _id
        set(_) {}

    override fun withEvents(events: List<OwnershipEvent>): Ownership {
        return copy(events = events)
    }

    companion object {

        fun parseId(id: String): OwnershipId {
            val parts = id.split(":")
            if (parts.size < 3) {
                throw IllegalArgumentException("Incorrect format of ownershipId: $id")
            }
            val tokenId = EthUInt256.of(parts[1].trim())
            return OwnershipId(Address.apply(parts[0].trim()), tokenId, Address.apply(parts[2].trim()))
        }

        fun empty(token: Address, tokenId: EthUInt256, owner: Address): Ownership {
            return Ownership(
                token = token,
                tokenId = tokenId,
                creators = emptyList(),
                owner = owner,
                value = EthUInt256.ZERO,
                lazyValue = EthUInt256.ZERO,
                date = nowMillis(),
                pending = emptyList()
            )
        }
    }
}
