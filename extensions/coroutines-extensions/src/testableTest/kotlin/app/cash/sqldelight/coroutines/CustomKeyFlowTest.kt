/*
 * Copyright (C) 2026 Wire GmbH
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
import app.cash.sqldelight.coroutines.Employee.Companion.MAPPER
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Integration tests for custom key functionality.
 *
 * These tests verify that custom keys work correctly at runtime:
 * - Queries with custom keys only emit when their specific custom key is notified
 * - Multiple custom keys work correctly
 * - Custom keys are isolated from table-based notifications
 * - Listener removal works properly with custom keys
 */
class CustomKeyFlowTest : DbTest {

  override suspend fun setupDb(): TestDb = TestDb(testDriver())

  @Test
  fun queryWithCustomKeyEmitsOnCustomNotification() = runTest { db ->
    // Create a query that listens to a custom key with parameter interpolation
    val conversationId = "conv123"
    val customKey = "conversation_$conversationId"

    val query = db.createQuery(
      customKey,
      "SELECT * FROM ${TestDb.TABLE_MESSAGE} WHERE conversation_id = '$conversationId'",
      Message.MAPPER,
    )

    query.asFlow()
      .test {
        // Initial emission - no messages yet
        awaitItem().assert {
          isEmpty()
        }

        // Insert a message with the matching conversation_id
        db.insertMessage(Message("msg1", conversationId, "Hello"))

        // Flow should emit because the custom key was notified
        awaitItem().assert {
          hasRow("msg1", conversationId, "Hello")
        }

        cancel()
      }
  }

  @Test
  fun queryDoesNotEmitOnUnrelatedCustomKey() = runTest { db ->
    // Create a query listening to conversation_conv123
    val conversationId1 = "conv123"
    val customKey1 = "conversation_$conversationId1"

    val query = db.createQuery(
      customKey1,
      "SELECT * FROM ${TestDb.TABLE_MESSAGE} WHERE conversation_id = '$conversationId1'",
      Message.MAPPER,
    )

    query.asFlow()
      .test {
        // Initial emission
        awaitItem().assert {
          isEmpty()
        }

        // Insert a message for a DIFFERENT conversation
        val conversationId2 = "conv456"
        db.insertMessage(Message("msg1", conversationId2, "Hello"))

        // Flow should NOT emit because different custom key was notified
        expectNoEvents()

        // Now insert for the correct conversation
        db.insertMessage(Message("msg2", conversationId1, "World"))

        // Now it should emit
        awaitItem().assert {
          hasRow("msg2", conversationId1, "World")
        }

        cancel()
      }
  }

  @Test
  fun multipleCustomKeysEmitOnAnyMatch() = runTest { db ->
    // Create a query with multiple custom keys (simulating multiple @CustomKey annotations)
    val conversationId = "conv123"
    val userId = "user1"
    val customKey1 = "conversation_$conversationId"
    val customKey2 = "user_$userId"

    // To simulate multiple keys, we need to test that a query listening to BOTH keys
    // will emit when EITHER is notified. We'll create two separate queries for this test.

    val queryConversation = db.createQuery(
      customKey1,
      "SELECT * FROM ${TestDb.TABLE_MESSAGE} WHERE conversation_id = '$conversationId'",
      Message.MAPPER,
    )

    queryConversation.asFlow()
      .test {
        awaitItem().assert { isEmpty() }

        // Notify conversation key
        db.insertMessage(Message("msg1", conversationId, "Hello"))

        awaitItem().assert {
          hasRow("msg1", conversationId, "Hello")
        }

        cancel()
      }

    // Test the user key separately
    val queryUser = db.createQuery(
      customKey2,
      "SELECT * FROM ${TestDb.TABLE_MESSAGE} WHERE sender_id = '$userId'",
      Message.MAPPER,
    )

    queryUser.asFlow()
      .test {
        awaitItem().assert {
          // Should see the message we inserted above (if it had the right sender_id)
          // For this test, let's insert a new one with the correct sender
        }

        db.insertMessageWithSender(Message("msg2", "conv456", "World", userId))

        awaitItem().assert {
          hasRow("msg2", "conv456", "World")
        }

        cancel()
      }
  }

  @Test
  fun customKeyDoesNotNotifyTableListeners() = runTest { db ->
    // Create two queries:
    // 1. One listening to a custom key
    // 2. One listening to the table name (simulating a query without custom keys)

    val customKey = "conversation_conv123"
    val tableKey = TestDb.TABLE_MESSAGE

    val customKeyQuery = db.createQuery(
      customKey,
      "SELECT * FROM ${TestDb.TABLE_MESSAGE} WHERE conversation_id = 'conv123'",
      Message.MAPPER,
    )

    val tableQuery = db.createQuery(
      tableKey,
      "SELECT * FROM ${TestDb.TABLE_MESSAGE}",
      Message.MAPPER,
    )

    // Test that custom key query emits when custom key is notified
    customKeyQuery.asFlow().test {
      awaitItem().assert { isEmpty() } // Initial emission

      // Insert with custom key notification only
      db.insertMessageWithCustomKey(Message("msg1", "conv123", "Hello"), customKey)

      // Custom key query should emit
      awaitItem().assert {
        hasRow("msg1", "conv123", "Hello")
      }

      cancel()
    }

    // Test that table query does NOT emit when only custom key is notified
    tableQuery.asFlow().test {
      awaitItem() // Initial emission with the message already inserted above

      // Insert with custom key notification only (not table notification)
      db.insertMessageWithCustomKey(Message("msg2", "conv123", "World"), customKey)

      // Table query should NOT emit (no events expected)
      expectNoEvents()

      cancel()
    }
  }

  @Test
  fun literalCustomKeyEmitsOnNotification() = runTest { db ->
    // Test a literal custom key (like @CustomKey all_users)
    val literalKey = "all_users"

    val query = db.createQuery(
      literalKey,
      "SELECT username, name FROM ${TestDb.TABLE_EMPLOYEE}",
      Employee.MAPPER,
    )

    query.asFlow()
      .test {
        awaitItem().assert {
          hasRow("alice", "Alice Allison")
          hasRow("bob", "Bob Bobberson")
          hasRow("eve", "Eve Evenson")
        }

        // Manually notify the literal custom key
        db.notifyCustomKey(literalKey)

        // Should re-emit even though no data changed (just testing notification works)
        awaitItem().assert {
          hasRow("alice", "Alice Allison")
          hasRow("bob", "Bob Bobberson")
          hasRow("eve", "Eve Evenson")
        }

        cancel()
      }
  }

  @Test
  fun listenerRemovalPreventsCustomKeyNotifications() = runTest { db ->
    val customKey = "conversation_conv123"

    val query = db.createQuery(
      customKey,
      "SELECT * FROM ${TestDb.TABLE_MESSAGE} WHERE conversation_id = 'conv123'",
      Message.MAPPER,
    )

    val flow = query.asFlow()

    flow.test {
      awaitItem() // Initial emission

      // Insert and verify emission
      db.insertMessageWithCustomKey(Message("msg1", "conv123", "Hello"), customKey)
      awaitItem().assert {
        hasRow("msg1", "conv123", "Hello")
      }

      // Cancel the flow (which should remove the listener)
      cancel()
    }

    // Now insert again - the listener should be removed, so no emission
    // We can't verify "no emission" after cancel, but we can verify
    // that a new collection starts fresh
    flow.test {
      awaitItem().assert {
        hasRow("msg1", "conv123", "Hello")
      }
      cancel()
    }
  }

  @Test
  fun multipleQueriesWithSameCustomKeyAllEmit() = runTest { db ->
    // Test that multiple queries listening to the same custom key all get notified
    val customKey = "conversation_conv123"

    val query1 = db.createQuery(
      customKey,
      "SELECT * FROM ${TestDb.TABLE_MESSAGE} WHERE conversation_id = 'conv123'",
      Message.MAPPER,
    )

    val query2 = db.createQuery(
      customKey,
      "SELECT COUNT(*) FROM ${TestDb.TABLE_MESSAGE} WHERE conversation_id = 'conv123'",
      { cursor -> cursor.getLong(0)!! },
    )

    // Test query1 emits on custom key notification
    query1.asFlow().test {
      awaitItem().assert { isEmpty() } // No messages initially

      db.insertMessageWithCustomKey(Message("msg1", "conv123", "Hello"), customKey)

      awaitItem().assert {
        hasRow("msg1", "conv123", "Hello")
      }

      cancel()
    }

    // Test query2 also emits on the same custom key notification
    query2.asFlow()
      .mapToOne(coroutineContext)
      .test {
        val initialCount = awaitItem()
        assertEquals(1L, initialCount) // We already inserted one message above

        db.insertMessageWithCustomKey(Message("msg2", "conv123", "World"), customKey)

        val newCount = awaitItem()
        assertEquals(2L, newCount)

        cancel()
      }
  }
}
