package kiwi.hoonkun.plugins.spoon.plugin

import io.ktor.server.websocket.*
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.server.LiveDataType
import kiwi.hoonkun.plugins.spoon.server.RunCommandResponse
import kiwi.hoonkun.plugins.spoon.server.SpoonLog
import kotlinx.coroutines.*
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LifeCycle
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.message.Message

class ConsoleFilter(private val parent: Main) : Filter {

    companion object {
        var owner: String? = null
        var pending = 0
            set(value) {
                if (value > 0) giveup()
                field = value
            }

        private val job = Job()
        private val pendingScope = CoroutineScope(Dispatchers.Default + job)
        private fun giveup() {
            job.cancel()
            pendingScope.launch {
                delay(5000)
                pending = 0
                owner = null
            }
        }
    }

    private val responseScope = CoroutineScope(Dispatchers.Default)

    private fun onResponse(response: String) {
        responseScope.launch {
            parent.subscribers(LiveDataType.CommandResponse)
                .find { it.username == owner }
                ?.session
                ?.sendSerialized(RunCommandResponse(LiveDataType.CommandResponse, response.replaceFirst("[Not Secure]", "")))
        }
    }

    override fun filter(event: LogEvent?): Filter.Result? {
        if (event == null) return null

        if (pending == 0) return null

        job.cancel()
        pending--

        onResponse(event.message.formattedMessage)

        parent.logs.add(SpoonLog(System.currentTimeMillis(), event.message.formattedMessage))
        return null
    }

    override fun getState(): LifeCycle.State? = null

    override fun initialize() { }

    override fun start() { }

    override fun stop() { }

    override fun isStarted(): Boolean = false

    override fun isStopped(): Boolean = false

    override fun getOnMismatch(): Filter.Result? = null

    override fun getOnMatch(): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, msg: String?, vararg params: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?, p0: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?, p0: Any?, p1: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?, p0: Any?, p1: Any?, p2: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?, p0: Any?, p1: Any?, p2: Any?, p3: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?,
        p0: Any?, p1: Any?, p2: Any?, p3: Any?, p4: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?,
        p0: Any?, p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?,
        p0: Any?, p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?, p6: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?,
        p0: Any?, p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?, p6: Any?, p7: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?,
        p0: Any?, p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?, p6: Any?, p7: Any?, p8: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, message: String?,
        p0: Any?, p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?, p6: Any?, p7: Any?, p8: Any?, p9: Any?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, msg: Any?, t: Throwable?
    ): Filter.Result? = null

    override fun filter(
        logger: Logger?, level: Level?, marker: Marker?, msg: Message?, t: Throwable?
    ): Filter.Result? = null

}