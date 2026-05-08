/*
 * Modifications Copyright (C) 2026 Wire GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.sqldelight.coroutines

import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.coroutines.TestDb.Companion.TABLE_EMPLOYEE
import app.cash.sqldelight.coroutines.TestDb.Companion.TABLE_MANAGER
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver

expect suspend fun testDriver(): SqlDriver

class TestDb(
  val db: SqlDriver,
) : SuspendingTransacterImpl(db),
  AutoCloseable {
  suspend fun init() {
    db.execute(null, "PRAGMA foreign_keys=ON", 0).await()

    db.execute(null, CREATE_EMPLOYEE, 0).await()
    val aliceId = employee(Employee("alice", "Alice Allison"))
    employee(Employee("bob", "Bob Bobberson"))
    val eveId = employee(Employee("eve", "Eve Evenson"))

    db.execute(null, CREATE_MANAGER, 0).await()
    manager(eveId, aliceId)

    // Initialize message table for custom key tests
    db.execute(null, CREATE_MESSAGE, 0).await()
  }

  fun <T : Any> createQuery(key: String, query: String, mapper: (SqlCursor) -> T): Query<T> {
    return object : Query<T>(mapper) {
      override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
        return db.executeQuery(null, query, mapper, 0, null)
      }

      override fun addListener(listener: Listener) {
        db.addListener(key, listener = listener)
      }

      override fun removeListener(listener: Listener) {
        db.removeListener(key, listener = listener)
      }
    }
  }

  fun notify(key: String) {
    db.notifyListeners(key)
  }

  fun notifyCustomKey(customKey: String) {
    db.notifyListeners(customKey)
  }

  override fun close() {
    db.close()
  }

  suspend fun insertMessage(message: Message): Long {
    db.await(
      3,
      """
      |INSERT INTO $TABLE_MESSAGE (${Message.ID}, ${Message.CONVERSATION_ID}, ${Message.CONTENT}, ${Message.SENDER_ID})
      |VALUES (?, ?, ?, ?)
      |
      """.trimMargin(),
      4,
    ) {
      bindString(0, message.id)
      bindString(1, message.conversationId)
      bindString(2, message.content)
      bindString(3, message.senderId)
    }
    // Notify with custom key pattern
    notify("conversation_${message.conversationId}")
    return transactionWithResult {
      val mapper: (SqlCursor) -> QueryResult<Long> = {
        it.next()
        QueryResult.Value(it.getLong(0)!!)
      }
      db.executeQuery(2, "SELECT last_insert_rowid()", mapper, 0).await()
    }
  }

  suspend fun insertMessageWithSender(message: Message): Long {
    db.await(
      4,
      """
      |INSERT INTO $TABLE_MESSAGE (${Message.ID}, ${Message.CONVERSATION_ID}, ${Message.CONTENT}, ${Message.SENDER_ID})
      |VALUES (?, ?, ?, ?)
      |
      """.trimMargin(),
      4,
    ) {
      bindString(0, message.id)
      bindString(1, message.conversationId)
      bindString(2, message.content)
      bindString(3, message.senderId)
    }
    // Notify with user custom key pattern
    notify("user_${message.senderId}")
    return transactionWithResult {
      val mapper: (SqlCursor) -> QueryResult<Long> = {
        it.next()
        QueryResult.Value(it.getLong(0)!!)
      }
      db.executeQuery(2, "SELECT last_insert_rowid()", mapper, 0).await()
    }
  }

  suspend fun insertMessageWithCustomKey(message: Message, customKey: String): Long {
    db.await(
      5,
      """
      |INSERT INTO $TABLE_MESSAGE (${Message.ID}, ${Message.CONVERSATION_ID}, ${Message.CONTENT})
      |VALUES (?, ?, ?)
      |
      """.trimMargin(),
      3,
    ) {
      bindString(0, message.id)
      bindString(1, message.conversationId)
      bindString(2, message.content)
    }
    // Notify ONLY the custom key, not the table
    notify(customKey)
    return transactionWithResult {
      val mapper: (SqlCursor) -> QueryResult<Long> = {
        it.next()
        QueryResult.Value(it.getLong(0)!!)
      }
      db.executeQuery(2, "SELECT last_insert_rowid()", mapper, 0).await()
    }
  }

  suspend fun employee(employee: Employee): Long {
    db.await(
      0,
      """
      |INSERT OR FAIL INTO $TABLE_EMPLOYEE (${Employee.USERNAME}, ${Employee.NAME})
      |VALUES (?, ?)
      |
      """.trimMargin(),
      2,
    ) {
      bindString(0, employee.username)
      bindString(1, employee.name)
    }
    notify(TABLE_EMPLOYEE)
    // last_insert_rowid is connection-specific, so run it in the transaction thread/connection
    return transactionWithResult {
      val mapper: (SqlCursor) -> QueryResult<Long> = {
        it.next()
        QueryResult.Value(it.getLong(0)!!)
      }
      db.executeQuery(2, "SELECT last_insert_rowid()", mapper, 0).await()
    }
  }

  suspend fun manager(
    employeeId: Long,
    managerId: Long,
  ): Long {
    db.await(
      1,
      """
      |INSERT OR FAIL INTO $TABLE_MANAGER (${Manager.EMPLOYEE_ID}, ${Manager.MANAGER_ID})
      |VALUES (?, ?)
      |
      """.trimMargin(),
      2,
    ) {
      bindLong(0, employeeId)
      bindLong(1, managerId)
    }
    notify(TABLE_MANAGER)
    // last_insert_rowid is connection-specific, so run it in the transaction thread/connection
    return transactionWithResult {
      val mapper: (SqlCursor) -> QueryResult<Long> = {
        it.next()
        QueryResult.Value(it.getLong(0)!!)
      }
      db.executeQuery(2, "SELECT last_insert_rowid()", mapper, 0).await()
    }
  }

  companion object {
    const val TABLE_EMPLOYEE = "employee"
    const val TABLE_MANAGER = "manager"
    const val TABLE_MESSAGE = "message"

    val CREATE_EMPLOYEE = """
      |CREATE TABLE $TABLE_EMPLOYEE (
      |  ${Employee.ID} INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
      |  ${Employee.USERNAME} TEXT NOT NULL UNIQUE,
      |  ${Employee.NAME} TEXT NOT NULL
      |)
    """.trimMargin()

    val CREATE_MANAGER = """
      |CREATE TABLE $TABLE_MANAGER (
      |  ${Manager.ID} INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
      |  ${Manager.EMPLOYEE_ID} INTEGER NOT NULL UNIQUE REFERENCES $TABLE_EMPLOYEE(${Employee.ID}),
      |  ${Manager.MANAGER_ID} INTEGER NOT NULL REFERENCES $TABLE_EMPLOYEE(${Employee.ID})
      |)
    """.trimMargin()

    val CREATE_MESSAGE = """
      |CREATE TABLE $TABLE_MESSAGE (
      |  ${Message.ID} TEXT NOT NULL PRIMARY KEY,
      |  ${Message.CONVERSATION_ID} TEXT NOT NULL,
      |  ${Message.CONTENT} TEXT NOT NULL,
      |  ${Message.SENDER_ID} TEXT
      |)
    """.trimMargin()
  }
}

data class Message(
  val id: String,
  val conversationId: String,
  val content: String,
  val senderId: String? = null,
) {
  companion object {
    const val ID = "id"
    const val CONVERSATION_ID = "conversation_id"
    const val CONTENT = "content"
    const val SENDER_ID = "sender_id"

    val MAPPER = { cursor: SqlCursor ->
      Message(
        id = cursor.getString(0)!!,
        conversationId = cursor.getString(1)!!,
        content = cursor.getString(2)!!,
        senderId = cursor.getString(3),
      )
    }
  }
}

object Manager {
  const val ID = "id"
  const val EMPLOYEE_ID = "employee_id"
  const val MANAGER_ID = "manager_id"

  val SELECT_MANAGER_LIST = """
    |SELECT e.${Employee.NAME}, m.${Employee.NAME}
    |FROM $TABLE_MANAGER AS manager
    |JOIN $TABLE_EMPLOYEE AS e
    |ON manager.$EMPLOYEE_ID = e.${Employee.ID}
    |JOIN $TABLE_EMPLOYEE AS m
    |ON manager.$MANAGER_ID = m.${Employee.ID}
    |
  """.trimMargin()
}

data class Employee(val username: String, val name: String) {
  companion object {
    const val ID = "id"
    const val USERNAME = "username"
    const val NAME = "name"

    const val SELECT_EMPLOYEES = "SELECT $USERNAME, $NAME FROM $TABLE_EMPLOYEE"

    val MAPPER = { cursor: SqlCursor ->
      Employee(cursor.getString(0)!!, cursor.getString(1)!!)
    }
  }
}
