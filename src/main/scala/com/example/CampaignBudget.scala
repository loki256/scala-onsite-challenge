package com.example

import java.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, ActorLogging}
import scala.concurrent.duration._

import org.joda.time.DateTime


class CampaignBudget(var currentBudget: Double, expireThreshold: Int) extends Actor with ActorLogging {

  import CampaignBudget._

  context.system.scheduler.schedule(
    0.milliseconds,
    60.seconds,
    self,
    ClearExpireBids
  )

  // auctionId -> ts
  case class BidAction(ts: Long, value: Double)
  var activeBids: Map[String, BidAction] = Map()

  def randomValueInRange(min: Double, max: Double): Double = {
    val r = new Random()
    min + (max - min) * r.nextDouble()
  }

  def createBid(auctionId: String): Bid = {
    val bidValue: Double = randomValueInRange(0.035, 0.055)
    // FIXME
    Bid(auctionId, bidValue, "USD", "http://videos-bucket.com/video123.mov", "something_from_config")
  }


  override def receive: Receive = {

    case BidRequest(auctionId) =>

      val bid = createBid(auctionId)
      if (currentBudget - bid.bid <= 0) {
        sender ! NoBid(auctionId)
      } else {
        currentBudget -= bid.bid
        log.debug(s"auctionId: $auctionId, currentBudget: $currentBudget")
        val currentTimeMs = DateTime.now.getMillis
        activeBids = activeBids.updated(auctionId, BidAction(currentTimeMs, bid.bid))
        sender ! bid
      }

    case WinningNotificationRequest(auctionId) =>
      log.debug(s"WinningNotificationRequest, $auctionId")
      activeBids.get(auctionId) match {
        case Some(bidAction) =>
          val currentTimeMs = DateTime.now.getMillis
          val diffMs = currentTimeMs - bidAction.ts
          val expired = diffMs >= expireThreshold
          if (expired) {
            log.info(s"Expired bid with auctionId: $auctionId, diff: $diffMs")
          } else {
            activeBids -= auctionId
          }
          sender ! WinningNotificationResponse(!expired)
        case _ =>
          sender ! WinningNotificationResponse(false)
      }

    case ClearExpireBids =>
      log.debug("ClearExpireBids")

      val currentTimeMs = DateTime.now.getMillis

      // expired items is a sequence of tuples (auctionId, bid price)
      val expiredItems: Seq[(String, Double)] = activeBids.flatMap { case (auctionId, bidAction) =>
          if (currentTimeMs - bidAction.ts >= expireThreshold) {
            Some((auctionId, bidAction.value))
          } else {
            None
          }
      }.toSeq

      // we need to return to the balance all expired bids
      currentBudget += expiredItems.map(_._2).sum

      // and we removed all expired items from the map
      activeBids = activeBids -- expiredItems.map(_._1)

      log.debug(s"Cleaning complete, activeBids: $activeBids, currentBudget: $currentBudget")
  }
}


object CampaignBudget {

  case class BidRequest(auctionId: String)
  case class WinningNotificationRequest(auctionId: String)

  case class WinningNotificationResponse(ok: Boolean)
  case object ClearExpireBids

}
