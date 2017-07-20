package com.example

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

object Boot {
  def main(args: Array[String]): Unit = {
    // we need an ActorSystem to host our application in
    implicit val system = ActorSystem("simple-dsp")
    implicit val materializer = ActorMaterializer()

    val campaignHolder = system.actorOf(Props(classOf[CampaignBudgetHolder], 20000), name="campaignHolder")
    val router = new DspFrontend(campaignHolder)

    Http().bindAndHandle(router(), "localhost", 8080)
  }
}
