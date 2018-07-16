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

import java.io.ByteArrayInputStream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import connectors.{ResidencyStatusAPIConnector, UserDetailsConnector}
import helpers.helpers.I18nHelper
import models._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers.{OK, contentAsString, _}
import play.api.test.{FakeRequest, Helpers}
import play.api.{Configuration, Environment}
import services.{SessionService, ShortLivedCache}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.Future


class WhatDoYouWantToDoControllerSpec extends UnitSpec with MockitoSugar with I18nHelper with WithFakeApplication {

  implicit val headerCarrier = HeaderCarrier()
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val currentTaxYear = TaxYearResolver.currentTaxYear

  val fakeRequest = FakeRequest()
  val mockAuthConnector = mock[AuthConnector]
  val mockUserDetailsConnector = mock[UserDetailsConnector]
  val mockSessionService = mock[SessionService]
  val mockShortLivedCache = mock[ShortLivedCache]
  val mockConfig = mock[Configuration]
  val mockEnvironment = mock[Environment]
  private val enrolmentIdentifier = EnrolmentIdentifier("PSAID", "Z123456")
  private val enrolment = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier), state = "Activated")
  private val enrolments = new Enrolments(Set(enrolment))
  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)
  val mockRasConnector = mock[ResidencyStatusAPIConnector]
  val mockUploadTimeStamp = new DateTime().minusDays(10).getMillis
  val mockExpiryTimeStamp = new DateTime().minusDays(7).getMillis
  val mockResultsFileMetadata = ResultsFileMetaData("",Some("testFile.csv"),Some(mockUploadTimeStamp),1,1L)
  val fileSession = FileSession(Some(CallbackData("","someFileId","",None)),Some(mockResultsFileMetadata),"1234",None,None)
  val userChoice = ""
  val rasSession = RasSession(userChoice ,MemberName("",""),MemberNino(""),MemberDateOfBirth(RasDate(None,None,None)),ResidencyStatusResult("",None,"","","","",""))

  val row1 = "John,Smith,AB123456C,1990-02-21"
  val inputStream = new ByteArrayInputStream(row1.getBytes)

  object TestWhatDoYouWantToDoController extends WhatDoYouWantToDoController {

    val authConnector: AuthConnector = mockAuthConnector
    override val userDetailsConnector: UserDetailsConnector = mockUserDetailsConnector
    override val resultsFileConnector: ResidencyStatusAPIConnector = mockRasConnector
    override val sessionService = mockSessionService
    override val shortLivedCache = mockShortLivedCache
    override val config: Configuration = mockConfig
    override val env: Environment = mockEnvironment

    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
    when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
    when(mockSessionService.hasUserDimissedUrBanner()(any())).thenReturn(Future.successful(false))
    when(mockRasConnector.getFile(any(), any())(any())).thenReturn(Future.successful(Some(inputStream)))
    when(mockUserDetailsConnector.getUserDetails(any())(any())).thenReturn(Future.successful(UserDetails(None, None, "", groupIdentifier = Some("group"))))
  }

  private def doc(result: Future[Result]): Document = Jsoup.parse(contentAsString(result))

  "get" should {

    "respond to GET /what-do-you-want-to-do" in {
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = TestWhatDoYouWantToDoController.get(fakeRequest)
      status(result) shouldBe OK
    }

    "contain the correct title and header" in {
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = TestWhatDoYouWantToDoController.get(fakeRequest)
      doc(result).title shouldBe Messages("whatDoYouWantToDo.page.title")
      doc(result).getElementsByClass("heading-xlarge").text shouldBe Messages("whatDoYouWantToDo.page.header")
    }

    "contain the single member h2" in {
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = TestWhatDoYouWantToDoController.get(fakeRequest)
      doc(result).getElementsByClass("task-list-section").get(0).html() shouldBe Messages("single.member.subheading")

    }

    "contain the enter a members detail link" in {
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = TestWhatDoYouWantToDoController.get(fakeRequest)
      doc(result).getElementsByClass("task-name").get(0).html() shouldBe Messages("enter.members.details")
      doc(result).getElementById("single-member-link").attr("href") should include("/member-name")
      doc(result).getElementById("single-member-link").attr("data-journey-click") shouldBe "navigation - link:What do you want to do:Enter a members details"

    }

    "contain the Multiple members h2" in {
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = TestWhatDoYouWantToDoController.get(fakeRequest)
      doc(result).getElementsByClass("task-list-section").get(1).html() shouldBe Messages("multiple.members.subheading")

    }
    "contain an Upload a file link" in {
      when(mockShortLivedCache.isFileInProgress(any())(any())).thenReturn(Future.successful(false))
      val result = TestWhatDoYouWantToDoController.get(fakeRequest)
      doc(result).getElementsByClass("task-name").get(1).html() shouldBe Messages("upload.file")
      doc(result).getElementById("upload-link").attr("href") should include("/upload-a-file")
      doc(result).getElementById("upload-link").attr("data-journey-click") shouldBe "navigation - link:What do you want to do:Upload a file"

    }
  }

//  "post" should {
//
//    "respond with bad request, must choose option" in {
//      val postData = Json.obj("userChoice" -> "")
//      val result = TestWhatDoYouWantToDoController.post.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
//      status(result) should equal(BAD_REQUEST)
//    }
//
//    "redirect to member name page when single lookup option is selected" in {
//      val rasSession = RasSession(WhatDoYouWantToDo.SINGLE ,MemberName("",""),MemberNino(""),MemberDateOfBirth(RasDate(None,None,None)),ResidencyStatusResult("",None,"","","","",""))
//      when(mockSessionService.cacheWhatDoYouWantToDo(any())(any())).thenReturn(Future.successful(Some(rasSession)))
//      val postData = Json.obj("userChoice" -> WhatDoYouWantToDo.SINGLE)
//      val result = TestWhatDoYouWantToDoController.post.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
//      redirectLocation(result).get should include("/member-name")
//    }
//
//    "redirect to file upload page when bulk lookup option is selected and there is no file session" in {
//      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
//      val rasSession = RasSession(WhatDoYouWantToDo.BULK ,MemberName("",""),MemberNino(""),MemberDateOfBirth(RasDate(None,None,None)),ResidencyStatusResult("",None,"","","","",""))
//      when(mockSessionService.cacheWhatDoYouWantToDo(any())(any())).thenReturn(Future.successful(Some(rasSession)))
//      val postData = Json.obj("userChoice" -> WhatDoYouWantToDo.BULK)
//      val result = TestWhatDoYouWantToDoController.post.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
//      redirectLocation(result).get should include("/upload-a-file")
//    }
//
//    "redirect to file upload page when bulk lookup option is selected and file session has no file" in {
//      val fileSession = FileSession(None,None,"1234",None,None)
//      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
//      val rasSession = RasSession(WhatDoYouWantToDo.BULK ,MemberName("",""),MemberNino(""),MemberDateOfBirth(RasDate(None,None,None)),ResidencyStatusResult("",None,"","","","",""))
//      when(mockSessionService.cacheWhatDoYouWantToDo(any())(any())).thenReturn(Future.successful(Some(rasSession)))
//      val postData = Json.obj("userChoice" -> WhatDoYouWantToDo.BULK)
//      val result = TestWhatDoYouWantToDoController.post.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
//      redirectLocation(result).get should include("/upload-a-file")
//    }
//
//    "redirect to file ready page when bulk lookup option is selected and there is a file ready" in {
//      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
//      when(mockSessionService.cacheWhatDoYouWantToDo(any())(any())).thenReturn(Future.successful(Some(rasSession.copy(userChoice = WhatDoYouWantToDo.BULK))))
//      val postData = Json.obj("userChoice" -> WhatDoYouWantToDo.BULK)
//      val result = TestWhatDoYouWantToDoController.post.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
//      redirectLocation(result).get should include("/file-ready")
//    }
//
//    "redirect to file result page when result option is selected and a result is ready" in {
//      val rasSession = RasSession(WhatDoYouWantToDo.RESULT ,MemberName("",""),MemberNino(""),MemberDateOfBirth(RasDate(None,None,None)),ResidencyStatusResult("",None,"","","","",""))
//      when(mockSessionService.cacheWhatDoYouWantToDo(any())(any())).thenReturn(Future.successful(Some(rasSession)))
//      when(mockShortLivedCache.failedProcessingUploadedFile(any())(any())).thenReturn(Future.successful(false))
//      val postData = Json.obj("userChoice" -> WhatDoYouWantToDo.RESULT)
//      val result = TestWhatDoYouWantToDoController.post.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
//      redirectLocation(result).get should include("/residency-status-added")
//    }
//
//    "redirect to file failed processing if it has been over 24 hours and no results file has been generated" in {
//      val rasSession = RasSession(WhatDoYouWantToDo.RESULT ,MemberName("",""),MemberNino(""),MemberDateOfBirth(RasDate(None,None,None)),ResidencyStatusResult("",None,"","","","",""))
//      when(mockSessionService.cacheWhatDoYouWantToDo(any())(any())).thenReturn(Future.successful(Some(rasSession)))
//      when(mockShortLivedCache.failedProcessingUploadedFile(any())(any())).thenReturn(Future.successful(true))
//      val postData = Json.obj("userChoice" -> WhatDoYouWantToDo.RESULT)
//      val result = TestWhatDoYouWantToDoController.post.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
//      redirectLocation(result).get should include("/problem-getting-results")
//    }
//
//    "redirect to global error page" when {
//      "no option is selected" in {
//        when(mockSessionService.cacheWhatDoYouWantToDo(any())(any())).thenReturn(Future.successful(Some(rasSession)))
//        val postData = Json.obj("userChoice" -> WhatDoYouWantToDo.RESULT)
//        val result = TestWhatDoYouWantToDoController.post.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
//        redirectLocation(result).get should include("/global-error")
//      }
//
//      "no session has been retrieved" in {
//        when(mockSessionService.cacheWhatDoYouWantToDo(any())(any())).thenReturn(Future.successful(None))
//        val postData = Json.obj("userChoice" -> WhatDoYouWantToDo.RESULT)
//        val result = TestWhatDoYouWantToDoController.post.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
//        redirectLocation(result).get should include("/global-error")
//      }
//    }
//  }

  "renderUploadResultsPage" should {

    "return ok when called" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      status(result) shouldBe OK
    }

    "return global error" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession.copy(userFile = None))))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      redirectLocation(result).get should include("/global-error")
    }

    "contain the correct page title" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).title shouldBe Messages("upload.result.page.title")
    }

    "contain a back link pointing to" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("back").attr("href") should include("/")
    }

    "contain the correct page header" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("page-header").text shouldBe Messages("upload.result.page.header")
    }

    "contain a icon file image" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("icon--file img").attr("src") should include("icon-file-download.png")
    }

    "contain a result link with the correct file name" in {
      val fileName = "originalFileName"
      val fileMetadata = FileMetadata("", Some(fileName), None)
      val fs = fileSession.copy(fileMetadata = Some(fileMetadata))
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fs)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("result-link").text shouldBe Messages("residency.status.result", fileName)
    }

    "contain a result link pointing to the results file" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("result-link").attr("href") should include(s"/results/${fileSession.userFile.get.fileId}")
    }

    "contain expiry date message" in {
      val expiryDate = new DateTime(mockExpiryTimeStamp)
      val formattedDate =  s"${expiryDate.toString("EEEE d MMMM yyyy")} at ${expiryDate.toString("H:mma").toLowerCase()}"
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("expiry-date-message").text shouldBe Messages("expiry.date.message",formattedDate)
    }

    "contain a what to do next header" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("whatnext-header").text shouldBe Messages("match.found.what.happens.next")
    }

    "contain what to do next content" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("whatnext-content").text shouldBe Messages("upload.result.what.next", Messages("upload.result.member.contact"))
    }

    "contain an contact HMRC link" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("contact-link").text shouldBe Messages("upload.result.member.contact")
    }

    "contains an HMRC link that points to help page" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("contact-link").attr("href") shouldBe ("https://www.gov.uk/government/organisations/hm-revenue-customs/contact/national-insurance-numbers")
    }

    "contain a deletion message" in {
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("deletion-message").text shouldBe Messages("deletion.message")
    }

    "contain the correct ga events when upload date is 01/01/2018 (CY+1)" in {
      val mockUploadTimeStamp = DateTime.parse("2018-01-01").getMillis
      val mockResultsFileMetadata = ResultsFileMetaData("",None,Some(mockUploadTimeStamp),1,1L)
      val fileSession = FileSession(Some(CallbackData("","someFileId","",None)),Some(mockResultsFileMetadata),"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("back").attr("data-journey-click") shouldBe "navigation - link:Residency status upload added CY & CY + 1:Back"
      doc(result).getElementById("result-link").attr("data-journey-click") shouldBe "link - click:Residency status upload added CY & CY + 1:ResidencyStatusResults CY & CY + 1 CSV"
      doc(result).getElementById("choose-something-else").attr("data-journey-click") shouldBe "button - click:Residency status upload added CY & CY + 1:Choose something else to do"
      doc(result).getElementById("contact-link").attr("data-journey-click") shouldBe "link - click:Residency status upload added CY & CY + 1:Member must contact HMRC"
    }

    "contain a cy message when upload date is 06/04/2018" in {
      val mockUploadTimeStamp = DateTime.parse("2018-04-06").getMillis
      val mockResultsFileMetadata = ResultsFileMetaData("",None,Some(mockUploadTimeStamp),1,1L)
      val fileSession = FileSession(Some(CallbackData("","someFileId","",None)),Some(mockResultsFileMetadata),"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("cy-message").text shouldBe Messages("cy.message", (currentTaxYear + 1).toString, (currentTaxYear + 2).toString)
    }

    "contain the correct ga events when upload date is 06/04/2018 (CY only)" in {
      val mockUploadTimeStamp = DateTime.parse("2018-04-06").getMillis
      val mockResultsFileMetadata = ResultsFileMetaData("",None,Some(mockUploadTimeStamp),1,1L)
      val fileSession = FileSession(Some(CallbackData("","someFileId","",None)),Some(mockResultsFileMetadata),"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("back").attr("data-journey-click") shouldBe "navigation - link:Residency status upload added CY:Back"
      doc(result).getElementById("result-link").attr("data-journey-click") shouldBe "link - click:Residency status upload added CY:ResidencyStatusResults CY CSV"
      doc(result).getElementById("choose-something-else").attr("data-journey-click") shouldBe "button - click:Residency status upload added CY:Choose something else to do"
      doc(result).getElementById("contact-link").attr("data-journey-click") shouldBe "link - click:Residency status upload added CY:Member must contact HMRC"

    }

    "contain a cy message when upload date is 31/12/2018" in {
      val mockUploadTimeStamp = DateTime.parse("2018-12-31").getMillis
      val mockResultsFileMetadata = ResultsFileMetaData("",None,Some(mockUploadTimeStamp),1,1L)
      val fileSession = FileSession(Some(CallbackData("","someFileId","",None)),Some(mockResultsFileMetadata),"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("cy-message").text shouldBe Messages("cy.message", (currentTaxYear + 1).toString, (currentTaxYear + 2).toString)
    }

    "contain a button to choose something else to do" in {
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("choose-something-else").text shouldBe Messages("choose.something.else")
    }

    "contain a button to choose something else to do which points to what do you want to do page" in {
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      doc(result).getElementById("choose-something-else").attr("href") should include("/")
    }

    "download a file containing the results" in {
      val mockUploadTimeStamp = DateTime.parse("2018-12-31").getMillis
      val mockResultsFileMetadata = ResultsFileMetaData("",Some("testFile.csv"),Some(mockUploadTimeStamp),1,1L)
      val fileSession = FileSession(Some(CallbackData("","someFileId","",None)),Some(mockResultsFileMetadata),"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.getResultsFile("testFile.csv").apply(
        FakeRequest(Helpers.GET, "/whatDoYouWantToDo/results/:testFile.csv")))
      contentAsString(result) shouldBe row1
    }

    "not be able to download a file containing the results when file name is incorrect" in {
      val mockUploadTimeStamp = DateTime.parse("2018-12-31").getMillis
      val mockResultsFileMetadata = ResultsFileMetaData("",Some("wrongName.csv"),Some(mockUploadTimeStamp),1,1L)
      val fileSession = FileSession(Some(CallbackData("","someFileId","",None)),Some(mockResultsFileMetadata),"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.getResultsFile("testFile.csv").apply(
        FakeRequest(Helpers.GET, "/whatDoYouWantToDo/results/:testFile.csv")))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/file-not-available")
    }

    "not be able to download a file containing the results when there is no results file" in {
      val fileSession = FileSession(Some(CallbackData("","someFileId","",None)),None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.getResultsFile("testFile.csv").apply(
        FakeRequest(Helpers.GET, "/whatDoYouWantToDo/results/:testFile.csv")))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/file-not-available")
    }

    "not be able to download a file containing the results when there is no file session" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.getResultsFile("testFile.csv").apply(
        FakeRequest(Helpers.GET, "/whatDoYouWantToDo/results/:testFile.csv")))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/file-not-available")
    }

    "redirect to error page" when {

      "render upload result page is called but there is no callback data in the retrieved file session" in {
        val fileSession = FileSession(None,Some(ResultsFileMetaData("",None,None,1,1L)),"1234",Some(new DateTime().plusDays(10).getMillis),None)
        when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
        val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/global-error")
      }
    }
  }

  "renderFileReadyPage" should {
    "return ok when called" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderFileReadyPage(fakeRequest))
      status(result) shouldBe OK
    }

    "return global error page" when {
      "there is no file session" in {
        when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
        val result = await(TestWhatDoYouWantToDoController.renderFileReadyPage(fakeRequest))
        redirectLocation(result).get should include("/global-error")
      }

      "there is a file session but there is no result file ready" in {
        val fileSession = FileSession(None,None,"1234",None,None)
        when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
        val result = await(TestWhatDoYouWantToDoController.renderFileReadyPage(fakeRequest))
        redirectLocation(result).get should include("/global-error")
      }
    }

    "contain the correct page title" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderFileReadyPage(fakeRequest))
      doc(result).title shouldBe Messages("file.ready.page.title")
    }

    "contain the correct page header" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderFileReadyPage(fakeRequest))
      doc(result).getElementById("header").text shouldBe Messages("file.ready.page.header")
    }

    "contain a back link pointing to /" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderFileReadyPage(fakeRequest))
      doc(result).getElementById("back").attr("href") should include("/")
    }

    "contains the correct sub header" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderFileReadyPage(fakeRequest))
      doc(result).getElementById("sub-header").text shouldBe Messages("file.ready.sub-header")
    }

    "the sub header contains a link that points to download results page" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderFileReadyPage(fakeRequest))
      doc(result).getElementById("sub-header-link").attr("href") should include("/residency-status-added")
    }

    "contain the correct ga events" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderFileReadyPage(fakeRequest))
      doc(result).getElementById("back").attr("data-journey-click") shouldBe "navigation - link:File ready:Back"
      doc(result).getElementById("sub-header-link").attr("data-journey-click") shouldBe "link - click:File ready:Download your file"
    }
  }

  "renderResultsNotAvailableYetPage" should {
    "return ok when called" in {
      val fileSession = FileSession(None,None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/results-not-available")
    }

    "contain the correct page title" in {
      val fileSession = FileSession(None,None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultsAvailableYetPage(fakeRequest))
      doc(result).title shouldBe Messages("results.not.available.yet.page.title")
    }

    "contain the correct page header" in {
      val fileSession = FileSession(None,None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultsAvailableYetPage(fakeRequest))
      doc(result).getElementById("header").text shouldBe Messages("results.not.available.yet.page.header")
    }

    "contain the correct sub header 1" in {
      val fileSession = FileSession(None,None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultsAvailableYetPage(fakeRequest))
      doc(result).getElementById("sub-header1").text shouldBe Messages("results.not.available.yet.sub-header1")
    }

    "contain the correct sub header 2" in {
      val fileSession = FileSession(None,None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultsAvailableYetPage(fakeRequest))
      doc(result).getElementById("sub-header2").text shouldBe Messages("results.not.available.yet.sub-header2")
    }

    "contain a back link pointing to /" in {
      val fileSession = FileSession(None,None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultsAvailableYetPage(fakeRequest))
      doc(result).getElementById("back").attr("href") should include("/")
    }

    "contain a choose something else to do button" in {
      val fileSession = FileSession(None,None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultsAvailableYetPage(fakeRequest))
      doc(result).getElementById("choose-something-else").text shouldBe Messages("choose.something.else")
    }

    "contain a choose something else to do button that points to /" in {
      val fileSession = FileSession(None,None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultsAvailableYetPage(fakeRequest))
      doc(result).getElementById("choose-something-else").attr("href") should include ("/")
    }

    "contain the correct ga events" in {
      val fileSession = FileSession(None,None,"1234",None,None)
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(Some(fileSession)))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultsAvailableYetPage(fakeRequest))
      doc(result).getElementById("back").attr("data-journey-click") shouldBe "navigation - link:Results are still being added:Back"
      doc(result).getElementById("choose-something-else").attr("data-journey-click") shouldBe "button - click:Results are still being added:Choose something else to do"
    }

  }

  "renderNoResultsAvailablePage" should {
    "return ok when called" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.renderUploadResultsPage(fakeRequest))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/no-results-available")
    }

    "contain the correct page title" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultAvailablePage.apply(fakeRequest))
      doc(result).title shouldBe Messages("no.results.available.page.title")
    }

    "contain the correct page header" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultAvailablePage.apply(fakeRequest))
      doc(result).getElementById("header").text shouldBe Messages("no.results.available.page.header")
    }

    "contain the correct page content" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultAvailablePage.apply(fakeRequest))
      doc(result).getElementById("sub-header").text shouldBe Messages("no.results.available.sub-header", Messages("no.results.available.link"))
    }

    "contain a back link pointing to /" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultAvailablePage.apply(fakeRequest))
      doc(result).getElementById("back").attr("href") should include("/")
    }

    "contain a choose something else to do button" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultAvailablePage.apply(fakeRequest))
      doc(result).getElementById("choose-something-else").text shouldBe Messages("choose.something.else")
    }

    "contain a choose something else to do button that points to /" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultAvailablePage.apply(fakeRequest))
      doc(result).getElementById("choose-something-else").attr("href") should include ("/")
    }

    "contain a link back to the upload a file page" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultAvailablePage.apply(fakeRequest))
      doc(result).getElementById("upload-link").attr("href") should include("/upload-a-file")
    }

    "contain the correct ga events" in {
      when(mockShortLivedCache.fetchFileSession(any())(any()))thenReturn(Future.successful(None))
      val result = await(TestWhatDoYouWantToDoController.renderNoResultAvailablePage.apply(fakeRequest))
      doc(result).getElementById("choose-something-else").attr("data-journey-click") shouldBe "button - click:You have not uploaded a file:Choose something else to do"
      doc(result).getElementsByClass("link-back").attr("data-journey-click") shouldBe "navigation - link:You have not uploaded a file:Back"
      doc(result).getElementById("upload-link").attr("data-journey-click") shouldBe "link - click:You have not uploaded a file:Upload a file"
    }
  }
}
