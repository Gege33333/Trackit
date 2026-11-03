package com.trackit.app.data

import java.util.UUID

enum class RecordType { PLUS, MINUS }

/** Amount is always stored as a positive number; [type] decides whether it is plus or minus. */
data class MoneyRecord(
    val id: String = UUID.randomUUID().toString(),
    val type: RecordType,
    val amount: Long,
    val description: String,
    val dateTime: Long, // epoch millis
)

data class Preset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Long,
)

data class UserAccount(val id: String, val name: String, val email: String)