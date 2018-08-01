/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import java.io.{BufferedReader, InputStreamReader}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import config.WSHttp
import models._
import org.mockito.Matchers.{eq => Meq, _}
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.ws.{DefaultWSResponseHeaders, StreamedResponse}
import uk.gov.hmrc.http.{HeaderCarrier, HttpPost, HttpResponse}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ResidencyStatusAPIConnectorSpec extends UnitSpec with OneAppPerSuite with MockitoSugar with ServicesConfig {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val mockWsHttp = mock[WSHttp]

  object TestConnector extends ResidencyStatusAPIConnector {
    override val http: HttpPost = mock[HttpPost]
    override val wsHttp: WSHttp = mockWsHttp

  }

  "Residency Status API connector" should {

    "send a get request to residency status service" when {
      "the date is between 1st January and 5th April" in {

        val memberDetails = MemberDetails(MemberName("John", "Smith"), "AB123456C", RasDate(Some("21"), Some("09"), Some("1970")))

        val expectedResponse = ResidencyStatus("scotResident", Some("otherUKResident"))

        when(TestConnector.http.POST[MemberDetails, ResidencyStatus](any(), any(), any())(any(), any(), any(), any())).thenReturn(Future.successful(expectedResponse))

        val result = TestConnector.getResidencyStatus(memberDetails)

        await(result) shouldBe expectedResponse

      }

      "the date is between 6th April and 31st December" in {

        val memberDetails = MemberDetails(MemberName("John", "Smith"), "AB123456C", RasDate(Some("21"), Some("09"), Some("1970")))

        val expectedResponse = ResidencyStatus("scotResident", None)

        when(TestConnector.http.POST[MemberDetails, ResidencyStatus](any(), any(), any())(any(), any(), any(), any())).thenReturn(Future.successful(expectedResponse))

        val result = TestConnector.getResidencyStatus(memberDetails)

        await(result) shouldBe expectedResponse
      }
    }
  }

  "getFile" should {

    "return an StreamedResponse from ras-api service" in {

      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()

      val streamResponse:StreamedResponse = StreamedResponse(DefaultWSResponseHeaders(200, Map("CONTENT_TYPE" -> Seq("application/octet-stream"))),
        Source.apply[ByteString](Seq(ByteString("Test"),  ByteString("\r\n"), ByteString("Passed")).to[scala.collection.immutable.Iterable]) )

      when(mockWsHttp.buildRequestWithStream(any())(any())).thenReturn(Future.successful(streamResponse))

      val values = List("Test", "Passed")

      val result = await(TestConnector.getFile("file1", "A1234567"))

      val reader = new BufferedReader(new InputStreamReader(result.get))

      (Iterator continually reader.readLine takeWhile (_ != null) toList) should contain theSameElementsAs List("Test", "Passed")

    }
  }

  "deleteFile" should {
    "return a 200 when a file has been successfully deleted" in {

      when(TestConnector.wsHttp.DELETE[HttpResponse](any())(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200)))

      val result = TestConnector.deleteFile("file-name", "userId")

      await(result).status shouldBe 200
    }

    "return a 500 when a file has not been deleted" in {
      when(TestConnector.wsHttp.DELETE[HttpResponse](any())(any(), any(), any())).thenReturn(Future.successful(HttpResponse(500)))

      val result = TestConnector.deleteFile("file-name", "userId")

      await(result).status shouldBe 500
    }
  }
}
