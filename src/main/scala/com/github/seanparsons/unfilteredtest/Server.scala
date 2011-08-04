package com.github.seanparsons.unfilteredtest

import unfiltered.netty.cycle.Plan
import unfiltered.request._
import unfiltered.response._
import akka.actor.Actor._
import akka.actor._
import akka.routing._
import akka.routing.Routing._
import akka.dispatch._
import java.io.File
import org.apache.commons.io.{FileUtils, IOUtils}
import unfiltered.netty.Http

object Server extends App {
  val tempDir = File.createTempFile("server", "test")
  tempDir.delete()
  tempDir.mkdirs()
  tempDir.deleteOnExit()
  val fileProcessingActor = loadBalancerActor(CyclicIterator(List.fill(10)(actorOf(new FileProcessingActor()).start()))).start()
  Http(8080).handler(new TestPlan(tempDir, fileProcessingActor)).run()
  registry.shutdownAll()
}

case class Read(file: File)
case class Write(file: File, text: String)

case class TestPlan(basePath: File, fileProcessingActor: ActorRef) extends Plan {
  def intent = {
    case GET(Path(Seg("test" :: "web" :: value :: Nil))) => {
      val text = fileProcessingActor.!!![String](Read(new File(basePath, value))).await.result.orElse(Some("")).get
      println(text)
      Ok ~> ResponseString(text)
    }
    case post@ POST(Path(Seg("test" :: "web" :: value :: Nil))) => {
      fileProcessingActor ! Write(new File(basePath, value), Body.string(post))
      Ok
    }
  }
}

case class FileProcessingActor() extends Actor {
  def receive = {
    case Read(file) => {
      val text = if (file.exists()) FileUtils.readFileToString(file) else ""
      self.reply_?(text)
    }
    case Write(file, text) => self.reply_?(FileUtils.writeStringToFile(file, text))
  }
}