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

package app.cash.sqldelight.core.queries

import app.cash.sqldelight.test.util.FixtureCompiler
import app.cash.sqldelight.test.util.fixtureRoot
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.test.assertFailsWith
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomKeyTest {
  @get:Rule val tempFolder = TemporaryFolder()

  @Test fun `query with literal custom key generates correct code`() {
    // Write the SQL to an actual file to preserve comments
    FixtureCompiler.writeSql(
      """
      |CREATE TABLE user (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  name TEXT NOT NULL
      |);
      |
      |selectAllUsers:
      |-- @CustomKey all_users
      |SELECT * FROM user;
      """.trimMargin(),
      tempFolder,
      fileName = "User.sq",
    )

    val result = FixtureCompiler.compileFixture(
      fixtureRoot = tempFolder.fixtureRoot().path,
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/UserQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    assertThat(result.compilerOutput[queriesFile].toString()).contains(
      """arrayOf("all_users")""",
    )
  }

  @Test fun `query with template custom key generates interpolated code`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL,
      |  content TEXT NOT NULL
      |);
      |
      |selectConversation:
      |-- @CustomKey conversation_:conversation_id
      |SELECT * FROM message WHERE conversation_id = :conversation_id;
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).contains("""driver.addListener("conversation_" + conversation_id, listener = listener)""")
  }

  @Test fun `query with multiple custom keys generates array with multiple keys`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL,
      |  user_id TEXT NOT NULL,
      |  content TEXT NOT NULL
      |);
      |
      |selectUserInConversation:
      |-- @CustomKey conversation_:conversation_id
      |-- @CustomKey user_:user_id
      |SELECT * FROM message
      |WHERE conversation_id = :conversation_id AND user_id = :user_id;
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).contains("""driver.addListener("conversation_" + conversation_id, "user_" + user_id, listener = listener)""")
  }

  @Test fun `query without custom key uses table-based keys`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  content TEXT NOT NULL
      |);
      |
      |selectAll:
      |SELECT * FROM message;
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    // Should use table name as key
    assertThat(queries).contains("""arrayOf("message")""")
  }

  @Test fun `mutation with custom notify key generates transaction-aware notifyQueries call`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL,
      |  content TEXT NOT NULL
      |);
      |
      |insertMessage:
      |-- @NotifyCustomKey conversation_:conversation_id
      |INSERT INTO message (id, content, conversation_id)
      |VALUES (?, ?, ?);
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    // Should notify custom key through the transaction-aware path
    assertThat(queries).contains("""notifyQueries("conversation_" + conversation_id)""")
    // Should NOT notify table-based listeners when custom keys are used
    assertThat(queries).doesNotContain("driver.notifyListeners")
  }

  @Test fun `mutation with literal custom notify key generates transaction-aware notifyQueries call`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE Member (
      |  id TEXT NOT NULL PRIMARY KEY
      |);
      |
      |deleteMember:
      |-- @NotifyCustomKey Member
      |DELETE FROM Member
      |WHERE id = :id;
      """.trimMargin(),
      tempFolder,
      fileName = "Member.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MemberQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).contains("""notifyQueries("Member")""")
    assertThat(queries).doesNotContain("driver.notifyListeners")
  }

  @Test fun `mutation with multiple notify keys generates transaction-aware notifyQueries call`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL,
      |  content TEXT NOT NULL
      |);
      |
      |updateMessage:
      |-- @NotifyCustomKey conversation_:conversation_id
      |-- @NotifyCustomKey message_:id
      |UPDATE message SET content = :content
      |WHERE id = :id AND conversation_id = :conversation_id;
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    // Should notify both custom keys
    assertThat(queries).contains("""notifyQueries("conversation_" + conversation_id, "message_" + id)""")
    // Should NOT notify table-based listeners when custom keys are used
    assertThat(queries).doesNotContain("driver.notifyListeners")
  }

  @Test fun `grouped mutation with notify keys inside group generates notifyQueries after transaction`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE Message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL,
      |  visibility TEXT NOT NULL
      |);
      |
      |CREATE TABLE MessageTextContent (
      |  message_id TEXT NOT NULL,
      |  conversation_id TEXT NOT NULL
      |);
      |
      |markMessageAsDeleted {
      |-- @NotifyCustomKey message_list_:conversation_id
      |-- @NotifyCustomKey conversation_list_last_message
      |-- @NotifyCustomKey Message
      |UPDATE Message
      |SET visibility = 'DELETED'
      |WHERE id = :message_id AND conversation_id = :conversation_id;
      |
      |DELETE FROM MessageTextContent
      |WHERE message_id = :message_id AND conversation_id = :conversation_id;
      |}
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).contains(
      """
      |  }.also {
      |    notifyQueries("message_list_" + conversation_id, "conversation_list_last_message", "Message")
      |  }
      """.trimMargin(),
    )
    assertThat(queries).doesNotContain("driver.notifyListeners")
  }

  @Test fun `grouped mutation with notify keys before group generates notifyQueries after transaction`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE Asset (
      |  key TEXT NOT NULL PRIMARY KEY
      |);
      |
      |CREATE TABLE MessageAssetContent (
      |  asset_id TEXT NOT NULL,
      |  conversation_id TEXT NOT NULL
      |);
      |
      |CREATE TABLE Message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL
      |);
      |
      |clearContent
      |-- @NotifyCustomKey message_list_:conversationId
      |-- @NotifyCustomKey conversation_list_last_message
      |{
      |DELETE FROM Asset WHERE key IN (
      |  SELECT asset_id FROM MessageAssetContent WHERE conversation_id = :conversationId
      |);
      |DELETE FROM Message WHERE conversation_id = :conversationId;
      |}
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).contains(
      """
      |  }.also {
      |    notifyQueries("message_list_" + conversationId, "conversation_list_last_message")
      |  }
      """.trimMargin(),
    )
    assertThat(queries).doesNotContain("driver.notifyListeners")
  }

  @Test fun `grouped query with result and notify keys generates notifyQueries after transaction`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL,
      |  content TEXT NOT NULL
      |);
      |
      |insertAndReturn {
      |-- @NotifyCustomKey conversation_:conversation_id
      |INSERT INTO message (id, conversation_id, content)
      |VALUES (:id, :conversation_id, :content);
      |
      |SELECT id, conversation_id, content
      |FROM message
      |WHERE id = :id;
      |}
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).contains("""notifyQueries("conversation_" + conversation_id)""")
    assertThat(queries).doesNotContain("driver.notifyListeners")
  }

  @Test fun `grouped query with result validates notify key parameters`() {
    val exception = assertFailsWith<Throwable> {
      FixtureCompiler.compileSql(
        """
        |CREATE TABLE message (
        |  id TEXT NOT NULL PRIMARY KEY,
        |  conversation_id TEXT NOT NULL
        |);
        |
        |insertAndReturn {
        |-- @NotifyCustomKey conversation_:missing_param
        |INSERT INTO message (id, conversation_id)
        |VALUES (:id, :conversation_id);
        |
        |SELECT id, conversation_id
        |FROM message
        |WHERE id = :id;
        |}
        """.trimMargin(),
        tempFolder,
        fileName = "Message.sq",
        enableCustomQueryKeys = true,
      )
    }

    val errorMessage = exception.cause?.message ?: exception.message ?: ""
    assertThat(errorMessage).contains(":missing_param")
    assertThat(errorMessage).contains("insertAndReturn")
  }

  @Test fun `grouped mutation notify key can reference parameter from later statement`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL
      |);
      |
      |CREATE TABLE messageTextContent (
      |  message_id TEXT NOT NULL,
      |  conversation_id TEXT NOT NULL
      |);
      |
      |clearContent {
      |-- @NotifyCustomKey conversation_:conversation_id
      |DELETE FROM message
      |WHERE id = :id;
      |
      |DELETE FROM messageTextContent
      |WHERE conversation_id = :conversation_id;
      |}
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).contains("""notifyQueries("conversation_" + conversation_id)""")
    assertThat(queries).doesNotContain("driver.notifyListeners")
  }

  @Test fun `notify key before non-first grouped statement is ignored`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL
      |);
      |
      |clearContent {
      |DELETE FROM message
      |WHERE id = :id;
      |
      |-- @NotifyCustomKey conversation_:conversation_id
      |DELETE FROM message
      |WHERE conversation_id = :conversation_id;
      |}
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).doesNotContain("""driver.notifyListeners("conversation_""")
    assertThat(queries).contains("notifyQueries")
  }

  @Test fun `notify key before single statement label is not attributed to that statement`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  content TEXT NOT NULL
      |);
      |
      |selectAll:
      |SELECT * FROM message;
      |
      |-- @NotifyCustomKey message_:id
      |insertMessage:
      |INSERT INTO message (id, content)
      |VALUES (:id, :content);
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).doesNotContain("""driver.notifyListeners("message_" + id)""")
    assertThat(queries).contains("notifyQueries")
  }

  @Test fun `custom key with invalid parameter reference fails`() {
    val exception = assertFailsWith<Throwable> {
      FixtureCompiler.compileSql(
        """
        |CREATE TABLE message (
        |  id TEXT NOT NULL PRIMARY KEY,
        |  conversation_id TEXT NOT NULL,
        |  content TEXT NOT NULL
        |);
        |
        |selectConversation:
        |-- @CustomKey conversation_:invalid_param
        |SELECT * FROM message WHERE conversation_id = :conversation_id;
        """.trimMargin(),
        tempFolder,
        fileName = "Message.sq",
        enableCustomQueryKeys = true,
      )
    }
    val errorMessage = exception.cause?.message ?: exception.message ?: ""
    assertThat(errorMessage).contains(":invalid_param")
    assertThat(errorMessage).contains("selectConversation")
  }

  @Test fun `custom key supports escaped colon literal`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE user (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  name TEXT NOT NULL
      |);
      |
      |selectAllUsers:
      |-- @CustomKey cache\:all_users
      |SELECT * FROM user;
      """.trimMargin(),
      tempFolder,
      fileName = "User.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/UserQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    assertThat(queries).contains("""arrayOf("cache:all_users")""")
  }

  @Test fun `custom key with dangling colon fails with source-aware error`() {
    val exception = assertFailsWith<Throwable> {
      FixtureCompiler.compileSql(
        """
        |CREATE TABLE message (
        |  id TEXT NOT NULL PRIMARY KEY
        |);
        |
        |selectMessage:
        |-- @CustomKey message_:
        |SELECT * FROM message;
        """.trimMargin(),
        tempFolder,
        fileName = "Message.sq",
        enableCustomQueryKeys = true,
      )
    }
    val errorMessage = exception.cause?.message ?: exception.message ?: ""
    assertThat(errorMessage).contains("':' must be followed by a parameter name")
  }

  @Test fun `custom key preserves parameter interpolation in addListener and removeListener`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL,
      |  content TEXT NOT NULL
      |);
      |
      |selectConversation:
      |-- @CustomKey conversation_:conversation_id
      |SELECT * FROM message WHERE conversation_id = :conversation_id;
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()

    // Check that the inner query class has proper listener methods
    assertThat(queries).contains("""addListener""")
    assertThat(queries).contains("""removeListener""")
    assertThat(queries).contains("""driver.addListener("conversation_" + conversation_id""")
    assertThat(queries).contains("""driver.removeListener("conversation_" + conversation_id""")
  }

  @Test fun `mutation without custom key uses table-based notification`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  content TEXT NOT NULL
      |);
      |
      |insertMessage:
      |INSERT INTO message (id, content) VALUES (?, ?);
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = true,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()
    // Should use notifyQueries with table name
    assertThat(queries).contains("notifyQueries")
  }

  @Test fun `custom key annotations are ignored when feature flag is disabled`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE message (
      |  id TEXT NOT NULL PRIMARY KEY,
      |  conversation_id TEXT NOT NULL,
      |  content TEXT NOT NULL
      |);
      |
      |selectConversation:
      |-- @CustomKey conversation_:conversation_id
      |SELECT * FROM message WHERE conversation_id = :conversation_id;
      |
      |insertMessage:
      |-- @NotifyCustomKey conversation_:conversation_id
      |INSERT INTO message (id, content, conversation_id)
      |VALUES (?, ?, ?);
      """.trimMargin(),
      tempFolder,
      fileName = "Message.sq",
      enableCustomQueryKeys = false,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MessageQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()

    // Should use table-based keys in addListener/removeListener
    assertThat(queries).contains("""driver.addListener("message", listener = listener)""")
    assertThat(queries).contains("""driver.removeListener("message", listener = listener)""")

    // Should NOT contain custom key listener code with conversation prefix
    assertThat(queries).doesNotContain("""driver.addListener("conversation_""")
    assertThat(queries).doesNotContain("""driver.removeListener("conversation_""")

    // Should NOT contain custom key notification code
    assertThat(queries).doesNotContain("""driver.notifyListeners("conversation_""")
    assertThat(queries).doesNotContain("""notifyQueries("conversation_""")

    // Should use standard table-based notification
    assertThat(queries).contains("notifyQueries")
    assertThat(queries).contains("""emit("message")""")
  }

  @Test fun `literal custom notify key is ignored when feature flag is disabled`() {
    val result = FixtureCompiler.compileSql(
      """
      |CREATE TABLE Member (
      |  id TEXT NOT NULL PRIMARY KEY
      |);
      |
      |deleteMember:
      |-- @NotifyCustomKey Member
      |DELETE FROM Member
      |WHERE id = :id;
      """.trimMargin(),
      tempFolder,
      fileName = "Member.sq",
      enableCustomQueryKeys = false,
    )

    assertThat(result.errors).isEmpty()
    val queriesFile = File(result.outputDirectory, "com/example/MemberQueries.kt")
    assertThat(result.compilerOutput).containsKey(queriesFile)
    val queries = result.compilerOutput[queriesFile].toString()

    assertThat(queries).doesNotContain("""notifyQueries("Member")""")
    assertThat(queries).doesNotContain("""driver.notifyListeners("Member")""")
    assertThat(queries).contains("notifyQueries")
    assertThat(queries).contains("""emit("Member")""")
  }
}
