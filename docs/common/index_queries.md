## Defining Typesafe Queries

SQLDelight will generate a typesafe function for any labeled SQL statement in a `.sq` file.

```sql title="src/main/sqldelight/com/example/sqldelight/hockey/data/Player.sq"
selectAll:
SELECT *
FROM hockeyPlayer;

insert:
INSERT INTO hockeyPlayer(player_number, full_name)
VALUES (?, ?);

insertFullPlayerObject:
INSERT INTO hockeyPlayer(player_number, full_name)
VALUES ?;
```

A "Queries" object will be generated for each `.sq` file containing labeled statements.
For example, a `PlayerQueries` object will be generated for the `Player.sq` file shown above.
This object can be used to call the generated typesafe functions which will execute the actual SQL
statements.

```kotlin
{% if async %}suspend {% endif %}fun doDatabaseThings(driver: SqlDriver) {
  val database = Database(driver)
  val playerQueries: PlayerQueries = database.playerQueries

  println(playerQueries.selectAll().{% if async %}await{% else %}execute{% endif %}AsList()) 
  // [HockeyPlayer(15, "Ryan Getzlaf")]

  playerQueries.insert(player_number = 10, full_name = "Corey Perry")
  println(playerQueries.selectAll().{% if async %}await{% else %}execute{% endif %}AsList()) 
  // [HockeyPlayer(15, "Ryan Getzlaf"), HockeyPlayer(10, "Corey Perry")]

  val player = HockeyPlayer(10, "Ronald McDonald")
  playerQueries.insertFullPlayerObject(player)
}
```

{% if async %}
!!! warning
    When using an asynchronous driver, use the suspending `awaitAs*()` extension functions when 
    running queries instead of the blocking `executeAs*()` functions.
{% endif %}

## Custom Query Keys

By default, SQLDelight uses observed table names as query listener keys.
You can opt into custom keys for finer-grained invalidation.

First, enable the feature in Gradle with `enableCustomQueryKeys = true` (or `enableCustomQueryKeys.set(true)` in Kotlin DSL).

Then annotate SQL statements with comments:

```sql
selectConversationMessages:
-- @CustomKey conversation_:conversation_id
SELECT id, conversation_id, content
FROM message
WHERE conversation_id = :conversation_id;

insertMessage:
-- @NotifyCustomKey conversation_:conversation_id
INSERT INTO message (id, conversation_id, content)
VALUES (?, ?, ?);
```

How it works:

- `@CustomKey` defines the key(s) a query listens to.
- `@NotifyCustomKey` defines the key(s) a mutation notifies.
- If custom keys are present, SQLDelight uses them instead of table-name notifications.
- Queries with `@CustomKey` are not invalidated by table-name notifications.
- `:parameter_name` inside a key expression interpolates the matching query or mutation parameter value.
- Use `\:` for a literal colon and `\\` for a literal backslash in key text.
- Referencing an unknown parameter fails compilation.

And that's it! Check out the other pages on the sidebar for other functionality.
