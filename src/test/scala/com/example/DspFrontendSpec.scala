package com.example

import akka.actor.Props
import org.scalatest.{FunSpec, Matchers}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest

class DspFrontendSpec extends FunSpec with Matchers with ScalatestRouteTest with BidResponseJsonFormats {

  private val campaignHolder = system.actorOf(Props(classOf[CampaignBudgetHolder], 10000), name="campaignHolder")
  private val router = new DspFrontend(campaignHolder)()

  describe("DspFrontend") {
    it("should return a proper bid response for bid_request") {
      pending

      Get("/bid_request?auction_id=6c831376-c1df-43ef-a377-85d83aa3314c&ip=127.0.0.1&bundle_name=com.facebook&connection_type=WiFi") ~>  router ~> check {
        status === StatusCodes.OK

        val response = responseAs[Bid]

        response.result shouldEqual "bid"
        response.auctionId shouldEqual "12"
        response.currency shouldEqual "USD"
        response.bid should (be  > 0.035 and be < 0.055)
      }
    }

    it ("should return a proper winner response for winner request") {
      Get("/winner/6c831376-c1df-43ef-a377-85d83aa3314c") ~> router ~> check {
        status === StatusCodes.OK
        responseAs[String] shouldEqual "OK"
      }
    }
  }
}
