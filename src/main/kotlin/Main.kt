import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getServer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.annotations.NotNull
import java.time.LocalDateTime
import kotlin.time.toJavaDuration
import java.time.Duration as JavaDuration
import kotlin.time.Duration as KotlinDuration
import kotlin.time.toKotlinDuration

class ShutdownNotifier : JavaPlugin(), Listener {
    override fun onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this)
        getCommand("shutdownNotify")!!.setExecutor(ShutdownNotifyCmd)
        logger.info("ShutdownNotifier enabled")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Determine if there's a shutdown scheduled when a user joins, so we can tell them
        if (ShutdownSentinel.isShutdownImminent) {
            event.player.sendMessage(ShutdownSentinel.getNotification())
        }
    }

    companion object {
        fun getPlugin(): ShutdownNotifier {
            return getPlugin(ShutdownNotifier::class.java)
        }
    }
}

object ShutdownSentinel {
    private const val TICKS_PER_SECOND = 20
    var isShutdownImminent = false
    private var message = ""
    private var initiator = ""
    private var shutdownTime: LocalDateTime? = null
    private var timer: KotlinDuration? = null

    fun shutdownNotify(sender: CommandSender, timer: KotlinDuration, message: @NotNull String) {
        // Check if someone else has initiated a shutdown request
        if (isShutdownImminent) {
            sender.sendPlainMessage("${this.initiator} already scheduled a shutdown")
            return
        }

        // TODO: Does this need to be synchronized?
        this.isShutdownImminent = true
        this.initiator = sender.name().toString()
        this.timer = timer
        this.shutdownTime = LocalDateTime.now()!!.plus(this.timer!!.toJavaDuration())
        this.message = message
        getServer().sendMessage(getNotification())

        if (this.timer!!.inWholeSeconds > 60) {
            // Let everyone know again 1 minute before the server shuts down
            Bukkit.getScheduler().runTaskLater(ShutdownNotifier.getPlugin(), { ->
                if (isShutdownImminent) {
                    getServer().sendMessage(getNotification())
                }
            }, TICKS_PER_SECOND * (this.timer!!.inWholeSeconds - 60))
        }

        // This shouldn't really happen but let's validate it just in case
        if (this.timer!!.inWholeSeconds > 5) {
            // Tell them 5 seconds before that the server is shutting down right now
            Bukkit.getScheduler().runTaskLater(ShutdownNotifier.getPlugin(), { ->
                if (isShutdownImminent) {
                    getServer().sendMessage(getNotification())
                }
            }, TICKS_PER_SECOND * (this.timer!!.inWholeSeconds - 5))
        }

        // Shutdown the server
        Bukkit.getScheduler().runTaskLater(ShutdownNotifier.getPlugin(), { ->
            if (isShutdownImminent) {
                getServer().shutdown()
            }
        }, TICKS_PER_SECOND * this.timer!!.inWholeSeconds)
    }

    fun getNotification(): Component {
        val duration = JavaDuration.between(LocalDateTime.now()!!, this.shutdownTime!!)
            .toKotlinDuration()
        // TODO: Check the duration and use the appropriate unit of measurement
        return Component.text("$message in ${duration.inWholeMinutes} minutes")
    }
}

object ShutdownNotifyCmd : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        val timer = KotlinDuration.parseOrNull(args?.get(0) ?: return false) ?: return false
        var message = args.drop(1).joinToString(separator = " ")

        // TODO: Configurable default message
        if (message.trim() == "") {
            message = "Server shutting down"
        }

        ShutdownSentinel.shutdownNotify(sender, timer, message)

        return true
    }
}