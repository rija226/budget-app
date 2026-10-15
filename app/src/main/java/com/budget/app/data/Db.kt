package com.budget.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class RawStatus { NEW, PARSED, FAILED, IGNORED }

/** Every notification is stored here before parsing, so nothing is lost if a parser is wrong. */
@Entity(tableName = "raw_notifications")
data class RawNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val status: RawStatus = RawStatus.NEW,
    val transactionId: Long? = null,
    /** Notification tag; in debug builds it names the test source of `adb shell cmd notification post`. */
    val tag: String? = null,
)

enum class TxType { EXPENSE, INCOME, TRANSFER }

/**
 * Amounts in the smallest unit (cents). [amountEurMinor] is null while the currency has no exchange rate.
 * [source]: "wallet", "bankA", "bankB", "manual", or two joined with "+" after a merge ("wallet+bankA").
 */
@Entity(tableName = "transactions", indices = [Index("occurredAt")])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val currency: String,
    val amountEurMinor: Long?,
    val type: TxType,
    val merchant: String,
    val categoryId: Long?,
    val categoryConfirmed: Boolean,
    val isSalary: Boolean = false,
    val occurredAt: Long,
    val source: String,
    val note: String? = null,
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limitEurMinor: Long? = null,
    val isIncome: Boolean = false,
)

@Entity(tableName = "merchant_rules")
data class MerchantRule(@PrimaryKey val merchantKey: String, val categoryId: Long)

enum class AnalysisState { LOADING, READY, FAILED }

/** Periods as epoch days, end exclusive. [json] is the parsed AI answer (see ai/Claude.kt). */
@Entity(tableName = "analyses")
data class Analysis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodStart: Long,
    val periodEnd: Long,
    val budgetMinor: Long,
    val state: AnalysisState,
    val json: String? = null,
    val error: String? = null,
    val keyProblem: Boolean = false,
    val createdAt: Long,
)

@Dao
interface RawNotificationDao {
    @Insert
    suspend fun insert(n: RawNotification): Long

    @Query("SELECT * FROM raw_notifications ORDER BY postedAt DESC")
    fun all(): Flow<List<RawNotification>>

    @Query("SELECT * FROM raw_notifications WHERE id = :id")
    suspend fun byId(id: Long): RawNotification?

    @Query("SELECT * FROM raw_notifications WHERE status IN ('NEW', 'FAILED') ORDER BY postedAt")
    suspend fun unprocessed(): List<RawNotification>

    /** Earlier notifications with the same text since [since]: re-posts of one notification. */
    @Query(
        "SELECT COUNT(*) FROM raw_notifications WHERE packageName = :pkg AND title IS :title AND text IS :text " +
            "AND id < :id AND postedAt >= :since",
    )
    suspend fun repeats(pkg: String, title: String?, text: String?, id: Long, since: Long): Int

    @Query("DELETE FROM raw_notifications WHERE packageName NOT IN (:keep)")
    suspend fun deleteExcept(keep: List<String>)

    @Query("UPDATE raw_notifications SET status = :status, transactionId = :txId WHERE id = :id")
    suspend fun setStatus(id: Long, status: RawStatus, txId: Long? = null)

    @Query("UPDATE raw_notifications SET transactionId = NULL, status = 'IGNORED' WHERE transactionId = :txId")
    suspend fun unlink(txId: Long)
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(t: Transaction): Long

    @Update
    suspend fun update(t: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): Transaction?

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun all(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    suspend fun allNow(): List<Transaction>

    /** Merge candidates: same amount and currency, close in time, not already containing [source]. */
    @Query(
        "SELECT * FROM transactions WHERE amountMinor = :amount AND currency = :currency " +
            "AND occurredAt BETWEEN :from AND :to AND source NOT LIKE '%' || :source || '%' AND source != 'manual' " +
            "ORDER BY ABS(occurredAt - :at) LIMIT 1",
    )
    suspend fun mergeCandidate(amount: Long, currency: String, at: Long, from: Long, to: Long, source: String): Transaction?

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCategory(from: Long, to: Long)

    @Query("SELECT * FROM transactions WHERE currency = :currency AND amountEurMinor IS NULL")
    suspend fun withoutRate(currency: String): List<Transaction>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun all(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun allNow(): List<Category>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Category?

    @Insert
    suspend fun insert(c: Category): Long

    @Update
    suspend fun update(c: Category)

    @Delete
    suspend fun delete(c: Category)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun get(key: String): MerchantRule?

    @Upsert
    suspend fun upsert(r: MerchantRule)

    @Query("UPDATE merchant_rules SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCategory(from: Long, to: Long)
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses ORDER BY periodStart DESC")
    fun all(): Flow<List<Analysis>>

    @Query("SELECT * FROM analyses WHERE id = :id")
    suspend fun byId(id: Long): Analysis?

    @Query("SELECT * FROM analyses WHERE periodStart = :start LIMIT 1")
    suspend fun forPeriod(start: Long): Analysis?

    @Insert
    suspend fun insert(a: Analysis): Long

    @Update
    suspend fun update(a: Analysis)
}

/** Proposed categories from CLAUDE.md; the owner renames, adds and deletes them. */
val DEFAULT_EXPENSE_CATEGORIES = listOf(
    "Market", "Restorani i kafići", "Dostava", "Gorivo i prevoz", "Režije", "Stanovanje", "Zdravlje",
    "Odjeća", "Zabava", "Pretplate", "Putovanja", "Gotovina", "Ostalo",
)
val DEFAULT_INCOME_CATEGORIES = listOf("Plata", "Ostali prihodi")

private fun seedCategories(db: SupportSQLiteDatabase) {
    (DEFAULT_EXPENSE_CATEGORIES.map { it to 0 } + DEFAULT_INCOME_CATEGORIES.map { it to 1 }).forEach { (name, income) ->
        db.execSQL("INSERT INTO categories (name, limitEurMinor, isIncome) VALUES (?, NULL, ?)", arrayOf<Any>(name, income))
    }
}

/** v1 -> v2: adds `tag` and the budget tables; the raw notifications collected in phase 1 stay. */
class SeedOnMigrate : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) = seedCategories(db)
}

@Database(
    entities = [RawNotification::class, Transaction::class, Category::class, MerchantRule::class, Analysis::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = SeedOnMigrate::class)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawNotifications(): RawNotificationDao
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun rules(): MerchantRuleDao
    abstract fun analyses(): AnalysisDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "budget.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seedCategories(db)
                })
                .build().also { instance = it }
        }
    }
}
