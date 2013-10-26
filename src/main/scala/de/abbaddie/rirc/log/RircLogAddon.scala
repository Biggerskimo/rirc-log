package de.abbaddie.rirc.log

import de.abbaddie.rirc.main._
import akka.actor.{Props, Actor}
import de.abbaddie.rirc.BackupFileProvider
import scala.collection.mutable
import org.joda.time.DateTime
import de.abbaddie.rirc.main.ConnectMessage
import java.io.FileWriter
import scala.concurrent.duration._

object RircLogAddon {
	def dateStr = DateTime.now.toString("yyyy-MM-dd")
	def timeStr = DateTime.now.toString("[dd.MM.yyyy HH:mm:ss.SSS]")
}

class RircLogAddon extends DefaultRircModule with RircAddon{
	def init() {
		val serverActor = Server.actorSystem.actorOf(Props[RircLogServerActor], "rirclog-global")
		Server.events.subscribeServer(serverActor)
	}
}

class RircLogServerActor extends Actor {
	var cache : mutable.Queue[String] = new mutable.SynchronizedQueue()

	def filename = "log/global." + RircLogAddon.dateStr + ".log"

	def receive = {
		case ConnectMessage(user) =>
			cache += RircLogAddon.timeStr + " CONNECT " + user.nickname + "(" + user.extendedString + ")\n"

		case NickchangeMessage(user, oldNick, newNick) =>
			cache += RircLogAddon.timeStr + " NICKCHANGE " + oldNick + " => " + newNick + "\n"

		case QuitMessage(user, None) =>
			cache += RircLogAddon.timeStr + " QUIT " + user.nickname + " [No Message]\n"

		case QuitMessage(user, Some(message)) =>
			cache += RircLogAddon.timeStr + " QUIT " + user.nickname + " (" + message + ")\n"

		case AuthSuccess(user, account) =>
			cache += RircLogAddon.timeStr + " AUTH " + user.nickname + ": " + account.id + "\n"

		case ChannelCreationMessage(channel, user) =>
			cache += RircLogAddon.timeStr + " NEWCHAN " + channel.name + " by " + user.nickname + "\n"
			val actor = Server.actorSystem.actorOf(Props(new RircLogChannelActor(channel)), "rirclog-" + channel.name.tail)
			Server.events.subscribe(actor, ChannelClassifier(channel))

		case ChannelCloseMessage(channel) =>
			cache += RircLogAddon.timeStr + " CLOSECHAN " + channel.name + "\n"
	}

	protected def save() {
		val writer = new FileWriter(filename, true)

		cache.dequeueAll(_ => true).foreach(writer.write)

		writer.close()
	}

	implicit val dispatcher = Server.actorSystem.dispatcher
	Server.actorSystem.scheduler.schedule(0 seconds, 5 seconds)(save())
}

class RircLogChannelActor(val channel : Channel) extends Actor {
	var cache : mutable.Queue[String] = new mutable.SynchronizedQueue()

	def filename = "log/channel-" + channel.name + "." + RircLogAddon.dateStr + ".log"

	def receive = {
		case BanMessage(_, user, mask) =>
			cache += RircLogAddon.timeStr + " BAN " + mask + " by " + mask + "\n"

		case ChannelCloseMessage(_) =>

		case ChannelCreationMessage(_, _) =>

		case ChannelModeChangeMessage(_, user, INVITE_ONLY(yes)) =>
			cache += RircLogAddon.timeStr + " MODE INVITE_ONLY=" + yes + " by " + user.nickname + "\n"

		case ChannelModeChangeMessage(_, user, PROTECTION(None)) =>
			cache += RircLogAddon.timeStr + " MODE PROTECTION OFF by " + user.nickname + "\n"

		case ChannelModeChangeMessage(_, user, PROTECTION(Some(password))) =>
			cache += RircLogAddon.timeStr + " MODE PROTECTION=" + password + " by " + user.nickname + "\n"

		case InvitationMessage(_, invitar, invited) =>
			cache += RircLogAddon.timeStr + " INVITE " + invited.username + " by " + invitar.nickname + "\n"

		case JoinMessage(_, user) =>
			cache += RircLogAddon.timeStr + " JOIN " + user.nickname + "\n"

		case KickMessage(_, kicker, kicked) =>
			cache += RircLogAddon.timeStr + " KICK " + kicked.nickname + " by " + kicker.nickname + "\n"

		case PartMessage(_, user, None) =>
			cache += RircLogAddon.timeStr + " PART " + user.nickname + " [No Message]\n"

		case PartMessage(_, user, Some(message)) =>
			cache += RircLogAddon.timeStr + " PART " + user.nickname + " (" + message + ")\n"

		case PublicNoticeMessage(_, user, text) =>
			cache += RircLogAddon.timeStr + " NOTICE " + user.nickname + ": " + text + "\n"

		case PublicTextMessage(_, user, text) =>
			cache += RircLogAddon.timeStr + " TEXT " + user.nickname + ": " + text + "\n"

		case ServiceCommandMessage(_, _, _, rest @ _*) =>

		case TopicChangeMessage(_, user, _, newTopic) =>
			cache += RircLogAddon.timeStr + " TOPIC " + user.nickname + ": " + newTopic.replace("\n", "\\n") + "\n"

		case UnbanMessage(_, user, mask) =>
			cache += RircLogAddon.timeStr + " UNBAN " + mask + " by " + mask + "\n"
	}

	protected def save() {
		val writer = new FileWriter(filename, true)

		cache.dequeueAll(_ => true).foreach(writer.write)

		writer.close()
	}

	implicit val dispatcher = Server.actorSystem.dispatcher
	Server.actorSystem.scheduler.schedule(0 seconds, 5 seconds)(save())
}
