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

package controllers

import connectors.{FileUploadConnector, UserDetailsConnector}
import helpers.helpers.I18nHelper
import models._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{atLeastOnce, verify, when}
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.OK
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, _}
import play.api.{Configuration, Environment}
import services.{SessionService, ShortLivedCache}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class FileUploadControllerSpec extends UnitSpec with WithFakeApplication with I18nHelper with MockitoSugar{

  implicit val headerCarrier = HeaderCarrier()

  val fakeRequest = FakeRequest()
  val mockAuthConnector = mock[AuthConnector]
  val mockFileUploadConnector = mock[FileUploadConnector]
  val mockUserDetailsConnector = mock[UserDetailsConnector]
  val mockSessionService = mock[SessionService]
  val mockShortLivedCache = mock[ShortLivedCache]
  val mockConfig = mock[Configuration]
  val mockEnvironment = mock[Environment]

  private val enrolmentIdentifier = EnrolmentIdentifier("PSAID", "Z123456")
  private val enrolment = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier), state = "Activated")
  private val enrolments = new Enrolments(Set(enrolment))
  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)
  val memberName = MemberName("Jackie","Chan")
  val memberNino = MemberNino("AB123456C")
  val memberDob = MemberDateOfBirth(RasDate(Some("12"),Some("12"),Some("2012")))
  val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""))
  val connectorResponse = HttpResponse(201,None,Map("Location" -> List("localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653")),None)

  val mockUploadTimeStamp = new DateTime().minusDays(10).getMillis
  val fileSession = FileSession(Some(CallbackData("","someFileId","",None)),None,"1234",Some(DateTime.now().getMillis()),None)

  private def doc(result: Future[Result]): Document = Jsoup.parse(contentAsString(result))

  object TestFileUploadController extends FileUploadController {
    val authConnector: AuthConnector = mockAuthConnector
    override val userDetailsConnector: UserDetailsConnector = mockUserDetailsConnector
    override val sessionService = mockSessionService
    override val shortLivedCache = mockShortLivedCache
    override val config: Configuration = mockConfig
    override val env: Environment = mockEnvironment
    override val fileUploadConnector = mockFileUploadConnector

    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(),any())).thenReturn(successfulRetrieval)
    when(mockUserDetailsConnector.getUserDetails(any())(any())).thenReturn(Future.successful(UserDetails(None, None, "", groupIdentifier = Some("group"))))
  }

  "FileUploadController" should {

    "render file upload page" when {
      "a url is successfully created from an envelope stored in the session" in {
        val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(false))
        val result = await(TestFileUploadController.get.apply(fakeRequest))
        status(result) shouldBe OK
        val expectedUrlPart = "file-upload/upload/envelopes/existingEnvelopeId123/files/"
        doc(result).getElementById("upload-form").attr("action") should include(expectedUrlPart)
      }

      "a url is successfully created using a new envelope where session does not exist" in {
        val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("0b215e97-11d4-4006-91db-c067e74fc653")))
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(None))
        when(mockFileUploadConnector.createEnvelope(any())(any())).thenReturn(Future.successful(connectorResponse))
        when(mockSessionService.cacheEnvelope(any())(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestFileUploadController.get.apply(fakeRequest))
        status(result) shouldBe OK
        val expectedUrlPart = "file-upload/upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/"
        doc(result).getElementById("upload-form").attr("action") should include(expectedUrlPart)
      }
    }

    "redirect to cannot upload another file page" when {
      "a file is already in process" in {
        val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(true))
        val result = await(TestFileUploadController.get.apply(fakeRequest))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/cannot-upload-another-file")
      }
    }

    "display file upload page when a file is not in processing" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")),Some(false))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(false))
      val result = await(TestFileUploadController.get.apply(fakeRequest))
      status(result) shouldBe OK
      doc(result).getElementById("header").text shouldBe Messages("file.upload.page.header")
    }

    "redirect to global error page" when {

      "the upload error endpoint in called by the file upload but caching fails" in {
        when(mockSessionService.cacheUploadResponse(Matchers.any())(Matchers.any())).thenReturn(Future.successful(None))
        val uploadRequest = FakeRequest(GET, "/relief-at-source/upload-error?errorCode=400&reason={%22error%22:{%22msg%22:%22Envelope%20does%20not%20allow%20zero%20length%20files,%20and%20submitted%20file%20has%20length%200%22}}")
        val result = await(TestFileUploadController.uploadError().apply(uploadRequest))
        redirectLocation(result).get should include("/file-upload-problem")
      }

      "a url is not successfully created from an existing envelope stored in the session" in {
        val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, None)
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        when(mockFileUploadConnector.createEnvelope(any())(any())).thenReturn(Future.failed(new RuntimeException))
        val result = TestFileUploadController.get().apply(fakeRequest)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/global-error")
      }

      "a new url is not successfully created" in {
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(None))
        when(mockFileUploadConnector.createEnvelope(any())(any())).thenReturn(Future.successful(connectorResponse))
        when(mockSessionService.cacheEnvelope(any())(Matchers.any())).thenReturn(Future.successful(None))
        val result = await(TestFileUploadController.get.apply(fakeRequest))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/global-error")
      }

      "session retrieval fails" in {
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.failed(new RuntimeException))
        val result = await(TestFileUploadController.get.apply(fakeRequest))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/global-error")
      }

      "upload success endpoint has been called but we fail to create a file session" in {
        val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        when(mockShortLivedCache.createFileSession(any(),any())(any())).thenReturn(Future.successful(false))
        val result = await(TestFileUploadController.uploadSuccess().apply(fakeRequest))
        redirectLocation(result).get should include("/global-error")
      }

      "upload success endpoint has been called but no envelope exists in the session" in {
        val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, None)
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestFileUploadController.uploadSuccess().apply(fakeRequest))
        redirectLocation(result).get should include("/global-error")
      }
    }

    "redirect to chooseAnOption page when back link is clicked" in {
      val result = TestFileUploadController.back.apply(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/")
    }

    "display file upload successful page" when {
      "file has been uploaded successfully" in {
        val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        when(mockShortLivedCache.createFileSession(any(),any())(any())).thenReturn(Future.successful(true))
        val result = await(TestFileUploadController.uploadSuccess().apply(fakeRequest))
        status(result) shouldBe OK
        doc(result).getElementById("page-header").text shouldBe Messages("upload.success.header")
      }
    }

    "successful upload page" should {
      "contain the correct content" in {
        val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        when(mockShortLivedCache.createFileSession(any(),any())(any())).thenReturn(Future.successful(true))
        val result = await(TestFileUploadController.uploadSuccess().apply(fakeRequest))
        doc(result).getElementById("page-header").text shouldBe Messages("upload.success.header")
        doc(result).getElementById("first-description").text shouldBe Messages("upload.success.first-description")
        doc(result).getElementById("second-description").text shouldBe Messages("upload.success.second-description")
        doc(result).getElementById("continue").text shouldBe Messages("continue")
      }

      "contains the correct ga events" in {
        val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        when(mockShortLivedCache.createFileSession(any(),any())(any())).thenReturn(Future.successful(true))
        val result = await(TestFileUploadController.uploadSuccess().apply(fakeRequest))
        doc(result).getElementById("continue").attr("data-journey-click") shouldBe "button - click:Your file has been uploaded:Continue"
      }
    }

    "create a file session when a file has been successfully uploaded" in {
      when(mockShortLivedCache.createFileSession(any(),any())(any())).thenReturn(Future.successful(true))
      val result = await(TestFileUploadController.uploadSuccess().apply(fakeRequest))
      verify(mockShortLivedCache, atLeastOnce()).createFileSession(any(),any())(any())
    }

    "redirect to file upload page" when {
      "the upload error endpoint in called by the file upload" in {
        when(mockSessionService.cacheUploadResponse(Matchers.any())(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val uploadRequest = FakeRequest(GET, "/relief-at-source/upload-error?errorCode=400&reason={%22error%22:{%22msg%22:%22Envelope%20does%20not%20allow%20zero%20length%20files,%20and%20submitted%20file%20has%20length%200%22}}")
        val result = await(TestFileUploadController.uploadError().apply(uploadRequest))
        redirectLocation(result).get should include("/upload-a-file")
      }
    }
  }

  "rendered file upload page" should {

    "contain a back link" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = TestFileUploadController.get().apply(fakeRequest)
      doc(result).getElementsByClass("link-back").text shouldBe Messages("back")
    }

    "contain 'upload file' title and header" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = TestFileUploadController.get().apply(fakeRequest)
      doc(result).title() shouldBe Messages("file.upload.page.title")
      doc(result).getElementById("header").text shouldBe Messages("file.upload.page.header")
    }

    "contain sub-header" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = TestFileUploadController.get().apply(fakeRequest)
      doc(result).getElementById("sub-header").html shouldBe Messages("file.upload.page.sub-header")
    }

    "contain 'choose file' button" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = TestFileUploadController.get().apply(fakeRequest)
      doc(result).getElementById("choose-file") shouldNot be(null)
    }

    "contain an upload button" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = TestFileUploadController.get().apply(fakeRequest)
      doc(result).getElementById("continue").text shouldBe Messages("continue")
    }

    "contain an uploading help link" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = TestFileUploadController.get().apply(fakeRequest)
      doc(result).getElementById("upload-help-link").text shouldBe Messages("get.help.uploading.link")
    }

    "contains the correct ga events" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = TestFileUploadController.get().apply(fakeRequest)
      doc(result).getElementById("continue").attr("data-journey-click") shouldBe "button - click:Upload a file:Continue"
      doc(result).getElementsByClass("link-back").attr("data-journey-click") shouldBe "navigation - link:Upload a file:Back"
      doc(result).getElementById("choose-file").attr("data-journey-click") shouldBe "button - click:Upload a file:Choose file"
      doc(result).getElementById("upload-help-link").attr("data-journey-click") shouldBe "link - click:Upload a file:Get help formatting your file"
    }

    "the get help link should be correct" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = TestFileUploadController.get().apply(fakeRequest)
      doc(result).getElementById("upload-help-link").attr("href") shouldBe "http://www.gov.uk/guidance/find-the-relief-at-source-residency-statuses-of-multiple-members"
    }

    "contain empty file error if present in session cache" in {
      val uploadResponse = UploadResponse("400",Some(Messages("file.upload.empty.file.reason")))
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse),Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      doc(result).getElementById("upload-error").text shouldBe Messages("file.empty.error")
    }

    "redirect to problem uploading file if a bad request has been submitted" in {
      val uploadResponse = UploadResponse("400",None)
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse),Some(Envelope("123456")))
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      redirectLocation(result).get should include("/file-upload-problem")
    }

    "contain file too large error if present in session cache" in {
      val uploadResponse = UploadResponse("413",Some(""))
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse),Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      doc(result).getElementById("upload-error").text shouldBe Messages("file.large.error")
    }

    "redirect to problem uploading file if envelope not found in session cache" in {
      val uploadResponse = UploadResponse("404",Some(""))
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse),Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      redirectLocation(result).get should include("/file-upload-problem")
    }

    "redirect to problem uploading file if file type is wrong" in {
      val uploadResponse = UploadResponse("415",Some(""))
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse),Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      redirectLocation(result).get should include("/file-upload-problem")
    }

    "redirect to problem uploading file if locked" in {
      val uploadResponse = UploadResponse("423",Some(""))
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse),Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      redirectLocation(result).get should include("/file-upload-problem")
    }

    "redirect to problem uploading file if server error" in {
      val uploadResponse = UploadResponse("500",None)
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse),Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      redirectLocation(result).get should include("/file-upload-problem")
    }

    "redirect to problem uploading file if any unknown errors" in {
      val uploadResponse1 = UploadResponse("500",None)
      val rasSession1 = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse1),Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession1)))
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      when(mockSessionService.cacheUploadResponse(any())(Matchers.any())).thenReturn(Future.successful(Some(rasSession1)))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      redirectLocation(result).get should include("/file-upload-problem")
    }

    "render file upload page id upload response contains no errors" in {
      val uploadResponse1 = UploadResponse("",None)
      val rasSession1 = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse1),Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession1)))
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      status(result) shouldBe OK
    }

    "redirect to global error page when upload response has not been cleared properly (no session returned form cache upload response)" in {
      val uploadResponse1 = UploadResponse("500",None)
      val rasSession1 = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),Some(uploadResponse1),Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession1)))
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      when(mockSessionService.cacheUploadResponse(any())(Matchers.any())).thenReturn(Future.successful(None))
      val result = await(TestFileUploadController.get().apply(fakeRequest))
      redirectLocation(result).get should include("/global-error")
    }


  }

  "cannot upload another file page" should {
    "contains the right title" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(true))
      when(mockShortLivedCache.fetchFileSession(any())(any())).thenReturn(Future.successful(Some(fileSession)))
      val result = TestFileUploadController.uploadInProgress().apply(fakeRequest)
      doc(result).title() shouldBe Messages("cannot.upload.another.file.page.title")
    }

    "return global error if there is no file session" in  {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(true))
      when(mockShortLivedCache.fetchFileSession(any())(any())).thenReturn(Future.successful(None))
      val result = TestFileUploadController.uploadInProgress().apply(fakeRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("global-error")
    }

    "redirect to file ready if there is a file session and the file is ready" in  {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(true))
      val mockResultsFileMetadata = ResultsFileMetaData("",Some("testFile.csv"),Some(mockUploadTimeStamp),1,1L)
      when(mockShortLivedCache.fetchFileSession(any())(any())).thenReturn(Future.successful(Some(fileSession.copy(resultsFile = Some(mockResultsFileMetadata)))))
      val result = TestFileUploadController.uploadInProgress().apply(fakeRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("file-ready")
    }

    "contains the right header" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(true))
      when(mockShortLivedCache.fetchFileSession(any())(any())).thenReturn(Future.successful(Some(fileSession)))
      val result = TestFileUploadController.uploadInProgress().apply(fakeRequest)
      doc(result).getElementById("page-header").text shouldBe Messages("cannot.upload.another.file.page.header")
    }

    "contains a reason paragraph" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(true))
      when(mockShortLivedCache.fetchFileSession(any())(any())).thenReturn(Future.successful(Some(fileSession)))
      val result = TestFileUploadController.uploadInProgress().apply(fakeRequest)
      doc(result).getElementById("page-reason").text shouldBe Messages("cannot.upload.another.file.page.reason")
    }

    "contains a clarification paragraph" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(true))
      when(mockShortLivedCache.fetchFileSession(any())(any())).thenReturn(Future.successful(Some(fileSession)))
      val result = TestFileUploadController.uploadInProgress().apply(fakeRequest)
      doc(result).getElementById("page-clarification").text shouldBe Messages("cannot.upload.another.file.page.clarification")
    }

    "contains a 'choose something else to do' button" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(true))
      when(mockShortLivedCache.fetchFileSession(any())(any())).thenReturn(Future.successful(Some(fileSession)))
      val result = TestFileUploadController.uploadInProgress().apply(fakeRequest)
      doc(result).getElementById("choose-something-else").text shouldBe Messages("choose.something.else")
    }

    "contains the correct ga events" in {
      val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("", None, "", "", "", "", ""), None, Some(Envelope("existingEnvelopeId123")))
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      when(mockShortLivedCache.isFileInProgress(any())(Matchers.any())).thenReturn(Future.successful(true))
      when(mockShortLivedCache.fetchFileSession(any())(any())).thenReturn(Future.successful(Some(fileSession)))
      val result = TestFileUploadController.uploadInProgress().apply(fakeRequest)
      doc(result).getElementById("choose-something-else").attr("data-journey-click") shouldBe "button - click:You cannot upload another file:Choose something else to do"
    }
  }

}
