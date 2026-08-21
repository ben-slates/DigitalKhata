package com.ben.khata.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "people", indices = [Index(value = ["name"], unique = true)])
data class Person(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long = System.currentTimeMillis())

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")]
)
data class KhataTransaction(@PrimaryKey(autoGenerate = true) val id: Long = 0, val personId: Long, val amount: Long, val note: String = "", val createdAt: Long = System.currentTimeMillis())

data class PersonSummary(val id: Long, val name: String, val total: Long, val entryCount: Long)

@Entity(tableName = "app_settings")
data class AppSettings(@PrimaryKey val id: Int = 1, val totalBudget: Long = 0)

data class TransactionWithPerson(
    val id: Long,
    val personId: Long,
    val amount: Long,
    val note: String,
    val createdAt: Long,
    val personName: String
)

@Dao
interface KhataDao {
    @Query("SELECT p.id, p.name, COALESCE(SUM(t.amount), 0) AS total, COUNT(t.id) AS entryCount FROM people p LEFT JOIN transactions t ON p.id = t.personId GROUP BY p.id ORDER BY p.name COLLATE NOCASE")
    fun people(): Flow<List<PersonSummary>>

    @Query("SELECT p.id, p.name, COALESCE(SUM(t.amount), 0) AS total, COUNT(t.id) AS entryCount FROM people p LEFT JOIN transactions t ON p.id = t.personId WHERE p.id = :personId GROUP BY p.id")
    fun person(personId: Long): Flow<PersonSummary?>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int = 5): Flow<List<KhataTransaction>>

    @Query("SELECT * FROM transactions WHERE personId = :personId ORDER BY createdAt DESC")
    fun transactions(personId: Long): Flow<List<KhataTransaction>>

    @Query("SELECT t.id, t.personId, t.amount, t.note, t.createdAt, p.name AS personName FROM transactions t INNER JOIN people p ON p.id = t.personId ORDER BY t.createdAt DESC")
    fun history(): Flow<List<TransactionWithPerson>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions") fun total(): Flow<Long>
    @Query("SELECT COUNT(*) FROM people") fun peopleCount(): Flow<Int>
    @Query("SELECT totalBudget FROM app_settings WHERE id = 1") fun budget(): Flow<Long?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(settings: AppSettings)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addPerson(person: Person): Long
    @Insert suspend fun addTransaction(transaction: KhataTransaction): Long
    @Query("UPDATE transactions SET amount = :amount, note = :note WHERE id = :id") suspend fun updateTransaction(id: Long, amount: Long, note: String)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTransaction(id: Long)
    @Query("DELETE FROM people WHERE id = :id") suspend fun deletePerson(id: Long)

    @Transaction
    suspend fun addEntry(name: String, amount: Long, note: String, existingPersonId: Long? = null) {
        val personId = existingPersonId ?: run {
            val cleanName = name.trim()
            personIdByNormalizedName(cleanName) ?: addPerson(Person(name = cleanName)).let { insertedId ->
                if (insertedId == -1L) personIdByNormalizedName(cleanName) ?: error("Unable to create person") else insertedId
            }
        }
        addTransaction(KhataTransaction(personId = personId, amount = amount, note = note.trim()))
    }
    @Query("SELECT id FROM people WHERE lower(trim(name)) = lower(trim(:name)) LIMIT 1") suspend fun personIdByNormalizedName(name: String): Long?
}

@Database(entities = [Person::class, KhataTransaction::class, AppSettings::class], version = 2, exportSchema = false)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun dao(): KhataDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS app_settings (id INTEGER NOT NULL, totalBudget INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        fun create(context: Context) = Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
