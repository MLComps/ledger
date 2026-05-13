package com.ledger.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock")
data class StockEntity(
  @PrimaryKey val item: String,
  val quantity: Double,
  val unit: String,
)
