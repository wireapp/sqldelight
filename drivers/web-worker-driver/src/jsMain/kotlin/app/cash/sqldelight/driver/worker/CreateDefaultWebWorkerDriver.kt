package app.cash.sqldelight.driver.worker

import app.cash.sqldelight.db.SqlDriver
import org.w3c.dom.Worker

actual fun createDefaultWebWorkerDriver(): SqlDriver {
  return WebWorkerDriver(Worker(js("""new URL("@wireapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)""")))
}
