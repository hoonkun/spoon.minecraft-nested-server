package kiwi.hoonkun.plugins.spoon.server.structures

import kotlinx.serialization.Serializable


@Serializable
data class User(
    val username: String,
    val password: String
)