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

package services

import models.{CallbackData, FileSession, ResultsFileMetaData}
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedHttpCaching}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import models.FileUploadStatus._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ShortLivedServiceSpec extends UnitSpec with OneAppPerSuite with ScalaFutures with MockitoSugar with BeforeAndAfter {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val fileId = "file-id-1"
  val fileStatus = "AVAILABLE"
  val reason: Option[String] = None
  val callbackData = CallbackData("1234", fileId, fileStatus, reason)
  val resultsFile = ResultsFileMetaData(fileId,Some("fileName.csv"),Some(1234L),123,1234L)
  val fileSession = FileSession(Some(callbackData), Some(resultsFile), "userId", Some(DateTime.now().getMillis), None)
  val fileSession1 = FileSession(Some(callbackData), None, "userId", Some(DateTime.now().minusDays(2).getMillis), None)
  val fileSession2 = FileSession(Some(callbackData), None, "userId2", Some(DateTime.now().minusHours(2).getMillis), None)


  val json = Json.toJson(fileSession)

  val mockSessionCache = mock[ShortLivedHttpCaching]
  val SUT = new ShortLivedCache {
    override val shortLivedCache: ShortLivedHttpCaching = mockSessionCache

    when(shortLivedCache.remove(any())(any(), any())).thenReturn(Future.successful(Future.successful(HttpResponse(200))))

    when(shortLivedCache.fetchAndGetEntry[FileSession] (any(), any(),any())
      (any(),any(), any()))
      .thenReturn(Future.successful(Some(fileSession)))

    when(shortLivedCache.cache[FileSession] (any(), any(),any(),any())
      (any[Writes[FileSession]], any[HeaderCarrier], any()))
      .thenReturn(Future.successful(CacheMap("sessionValue", Map("1234" -> json))))
  }

  "ShortLivedService" should {
    "cache fileSession in sav4later" in {
      val results = List("Nino, firstName, lastName, dob, cyResult, cy+1Result")
      val res = await(SUT.createFileSession("1234","56789"))
      res shouldBe true
    }
    "return false on failing to cache fileSession data" in {
      when(mockSessionCache.cache[FileSession] (any(), any(),any(),any())(any[Writes[FileSession]], any[HeaderCarrier], any())).thenReturn(Future.failed(new Exception))
      val res = await(SUT.createFileSession("1234","56789"))
      res shouldBe false
    }
    "should get cached fileSession from ShortLivedCache" in {
      val res = await(SUT.fetchFileSession("1234"))
      res.get shouldBe fileSession
    }
    "return none on failing to get cached data" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.failed(new Exception))
      val res = await(SUT.fetchFileSession("1234"))
      res shouldBe None
    }
    " return false if a file is not uploaded by the user" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(None))

      val res = await(SUT.isFileInProgress("userId"))
      res shouldBe false
    }

    " return true a file is uploaded before and is in progress" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(Some(fileSession)))

      val res = await(SUT.isFileInProgress("userId"))
      res shouldBe true
    }
    "return true if a results file is available in fileSession" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(Some(fileSession)))

      val res = await(SUT.isFileInProgress("userId"))
      res shouldBe true
    }
    "return false if a file uploaded time is more than 24 hours and no results file" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(Some(fileSession1)))

      val res = await(SUT.isFileInProgress("userId"))
      res shouldBe false
    }
    "return true if a file uploaded time is less than 24 hours and no results file" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(Some(fileSession2)))

      val res = await(SUT.isFileInProgress("userId2"))
      res shouldBe true
    }
    "return false failing to get fileSession to check if the file is in progress" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.failed(new Exception))
      val res = await(SUT.isFileInProgress("userId2"))
      res shouldBe false
    }
    "removes fileSession from cache" in {
      when(mockSessionCache.remove ("56789")).thenReturn(Future.successful(HttpResponse(202)))
      val res = await(SUT.removeFileSessionFromCache("56789"))
      res shouldBe 202
    }

    "return the correct file status" when {
      "file session does not exist" in {
        when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(), any(), any())).thenReturn(Future.successful(None))

        val res = await(SUT.determineFileStatus("userId"))
        res shouldBe NoFileSession
      }

      "file session exists and file is ready" in {
        when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(), any(), any())).thenReturn(Future.successful(Some(fileSession)))

        val res = await(SUT.determineFileStatus("userId"))
        res shouldBe Ready
      }

      "file session exists and file is in progress" in {
        when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(), any(), any())).thenReturn(Future.successful(Some(fileSession2)))

        val res = await(SUT.determineFileStatus("userId"))
        res shouldBe InProgress

      }
      "file session exists and more then 24 hours has passed" in {
        when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(), any(), any())).thenReturn(Future.successful(Some(fileSession1)))

        val res = await(SUT.determineFileStatus("userId"))
        res shouldBe TimeExpiryError

      }

      "file session exists and there is a problem with the file upload process" in {
        val cd = callbackData.copy(status = "ERROR")
        val fileSession = FileSession(Some(cd), None, "userId", Some(DateTime.now().minusHours(2)getMillis), None)
        when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(Some(fileSession)))

        val res = await(SUT.determineFileStatus("userId"))
        res shouldBe UploadError
      }

    }
  }

  "failedProcessingUploadedFile" should {

    "return true if it has been 24 hours since upload and no results file exists" in {
      val fileSession = FileSession(Some(callbackData), None, "userId", Some(DateTime.now().minusDays(2)getMillis), None)
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(Some(fileSession)))
      val res = await(SUT.failedProcessingUploadedFile("userId"))
      res shouldBe true
    }

    "return false if it hasn't been 24 hours since upload and no results file exists" in {
      val fileSession = FileSession(Some(callbackData), None, "userId2", Some(DateTime.now().minusHours(2)getMillis), None)
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(Some(fileSession)))
      val res = await(SUT.failedProcessingUploadedFile("userId"))
      res shouldBe false
    }

    "return false if no upload timestamp has been found" in {
      val fileSession = FileSession(Some(callbackData), Some(resultsFile), "userId", None, None)
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(Some(fileSession)))
      val res = await(SUT.failedProcessingUploadedFile("userId"))
      res shouldBe false
    }

    "return false if no file session has been found" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())(any(),any(), any())).thenReturn(Future.successful(None))
      val res = await(SUT.failedProcessingUploadedFile("userId"))
      res shouldBe false
    }

  }
}
