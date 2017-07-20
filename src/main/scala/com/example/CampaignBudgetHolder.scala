package com.example

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.example.CampaignBudget.{WinningNotificationRequest, WinningNotificationResponse}

import scala.concurrent.Future

class CampaignBudgetHolder(expireThreshold: Int = 200) extends Actor with ActorLogging {
  /** This class create and holds campaigns and also proxies requests to necessary campaigns by bundle_name */

  implicit val timeout: Timeout = 10.seconds

  import akka.pattern.pipe
  import akka.pattern.ask

  import CampaignBudgetHolder._

  // just manual initialization. In real application this could be initialized from db
  val campaignsMap = Map(
    "com.rovio.angry_birds" -> context.actorOf(Props(classOf[CampaignBudget], 50000.0, expireThreshold), name="com.rovio.angry_birds"),
    "com.spotify" -> context.actorOf(Props(classOf[CampaignBudget], 50000.0, expireThreshold), name="com.spotify"),
    "com.facebook" -> context.actorOf(Props(classOf[CampaignBudget], 50000.0, expireThreshold), name="com.facebook")
  )

  override def receive: Receive = {
    case ProxyBidRequest(bundleName: String, auctionId: String) =>
      campaignsMap.get(bundleName) match {
        case Some(campaign) =>
          log.info(s"Found campaign for bundle name: $bundleName")
          val future = campaign ? CampaignBudget.BidRequest(auctionId)
          future.pipeTo(sender)
        case None =>
          log.warning(s"Can't find actor with bundleName: $bundleName")
          sender ! NoBid(auctionId)
      }

    case ProxyWinningNotificationRequest(auctionId: String) =>
      val futureList = campaignsMap.values.map { actor =>
        (actor ? WinningNotificationRequest(auctionId)).mapTo[WinningNotificationResponse]
      }.toList

      val savedSender = sender

      // wait for responses from all actors and if any returns ok we returns ok
      Future.sequence(futureList).map { x =>
        log.debug("All futures done")
        val res = x.exists(_.ok)
        savedSender ! WinningNotificationResponse(res)
      }

    case x =>
      log.warning(s"Unknown message: $x")
  }
}

object CampaignBudgetHolder {
  case class ProxyBidRequest(bundleName: String, auctionId: String)
  case class ProxyWinningNotificationRequest(auctionId: String)
}
