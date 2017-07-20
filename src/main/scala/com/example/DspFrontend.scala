package com.example

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import com.example.CampaignBudget.WinningNotificationResponse
import com.example.CampaignBudgetHolder.{ProxyBidRequest, ProxyWinningNotificationRequest}

import scala.concurrent.duration._

/*
 * Add your logic here. Feel free to rearrange the code as you see fit,
 * this is just a starting point.
 */
class DspFrontend(campaignHolder: ActorRef) extends Directives with BidResponseJsonFormats {

  import akka.pattern.ask
  implicit val timeout: Timeout = 10.seconds

  def apply(): Route =
    path("bid_request") {
      get {
        parameters('auction_id, 'ip, 'bundle_name, 'connection_type) { (auction_id, ip, bundle_name, connection_type) =>
          val bid = (campaignHolder ? ProxyBidRequest(bundle_name, auction_id)).mapTo[BidResponse]
          complete(bid)
        }
      }
    } ~ path("winner" / Segment) { auctionId =>
      get {
        val resp = (campaignHolder ? ProxyWinningNotificationRequest(auctionId)).mapTo[WinningNotificationResponse]
        onSuccess(resp) {
          case WinningNotificationResponse(true) =>
            complete("OK")
          case WinningNotificationResponse(false) =>
            complete(StatusCodes.NotFound)
        }
      }
    }
}
