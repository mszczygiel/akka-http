/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.http.scaladsl.unmarshalling

import akka.http.scaladsl.unmarshalling.Unmarshaller.EitherUnmarshallingException
import org.scalatest.{ BeforeAndAfterAll, FreeSpec, Matchers }
import akka.http.scaladsl.testkit.ScalatestUtils
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.testkit._
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.Await

class UnmarshallingSpec extends FreeSpec with Matchers with BeforeAndAfterAll with ScalatestUtils {
  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  override val testConfig = ConfigFactory.load()

  "The PredefinedFromEntityUnmarshallers" - {
    "stringUnmarshaller should unmarshal `text/plain` content in UTF-8 to Strings" in {
      Unmarshal(HttpEntity("Hällö")).to[String] should evaluateTo("Hällö")
    }
    "stringUnmarshaller should assume UTF-8 for textual content type with missing charset" in {
      Unmarshal(HttpEntity(MediaTypes.`text/plain`.withMissingCharset, "Hällö".getBytes("UTF-8"))).to[String] should evaluateTo("Hällö")
    }
    "charArrayUnmarshaller should unmarshal `text/plain` content in UTF-8 to char arrays" in {
      Unmarshal(HttpEntity("árvíztűrő ütvefúrógép")).to[Array[Char]] should evaluateTo("árvíztűrő ütvefúrógép".toCharArray)
    }
  }

  "The PredefinedFromStringUnmarshallers" - {
    "booleanUnmarshaller should unmarshal '1' => true '0' => false" in {
      Unmarshal("1").to[Boolean] should evaluateTo(true)
      Unmarshal("0").to[Boolean] should evaluateTo(false)
    }
  }

  "The GenericUnmarshallers" - {
    implicit val rawInt: FromEntityUnmarshaller[Int] = Unmarshaller(implicit ex ⇒ bs ⇒ bs.toStrict(1.second.dilated).map(_.data.utf8String.toInt))
    implicit val rawlong: FromEntityUnmarshaller[Long] = Unmarshaller(implicit ex ⇒ bs ⇒ bs.toStrict(1.second.dilated).map(_.data.utf8String.toLong))

    "eitherUnmarshaller should unmarshal its Right value" in {
      // we'll find:
      // PredefinedFromEntityUnmarshallers.eitherUnmarshaller[String, Int] will be found
      //
      // which finds:
      //   rawInt: FromEntityUnmarshaller[Int]
      //  +
      //   stringUnmarshaller: FromEntityUnmarshaller[String]

      val testRight = Unmarshal(HttpEntity("42")).to[Either[String, Int]]
      Await.result(testRight, 1.second.dilated) should ===(Right(42))
    }

    "eitherUnmarshaller should unmarshal its Left value" in {
      val testLeft = Unmarshal(HttpEntity("I'm not a number, I'm a free man!")).to[Either[String, Int]]
      Await.result(testLeft, 1.second.dilated) should ===(Left("I'm not a number, I'm a free man!"))
    }

    "eitherUnmarshaller report both error messages if unmarshalling failed" in {
      type ImmenseChoice = Either[Long, Int]
      val testLeft = Unmarshal(HttpEntity("I'm not a number, I'm a free man!")).to[ImmenseChoice]
      val ex = intercept[EitherUnmarshallingException] {
        Await.result(testLeft, 1.second.dilated)
      }

      ex.getMessage should include("Either[long, int]")
      ex.getMessage should include("attempted int first")
      ex.getMessage should include("Right failure: For input string")
      ex.getMessage should include("Left failure: For input string")
    }
  }

  "Unmarshaller.forContentTypes" - {
    "should handle media ranges of types with missing charset by assuming UTF-8 charset when matching" in {
      val um = Unmarshaller.stringUnmarshaller.forContentTypes(MediaTypes.`text/plain`)
      Await.result(um(HttpEntity(MediaTypes.`text/plain`.withMissingCharset, "Hêllö".getBytes("utf-8"))), 1.second.dilated) should ===("Hêllö")
    }
  }

  override def afterAll() = TestKit.shutdownActorSystem(system)
}
