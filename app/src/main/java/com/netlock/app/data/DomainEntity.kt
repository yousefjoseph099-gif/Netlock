package com.netlock.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ListType { WHITELIST, BLACKLIST }

@Entity(tableName = "domains", indices = [androidx.room.Index(value = ["domain", "listType"], unique = true)])
data class DomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val listType: ListType
)
