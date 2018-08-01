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

import java.io.File

import connectors.UserDetailsConnector
import helpers.RandomNino
import helpers.helpers.I18nHelper
import models._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.{Configuration, Environment, Mode}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, _}
import services.SessionService
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.Future

class ResultsControllerSpec extends UnitSpec with WithFakeApplication with I18nHelper with MockitoSugar {

  val fakeRequest = FakeRequest("GET", "/")
  val currentTaxYear = TaxYearResolver.currentTaxYear

  val SCOTTISH = "Scotland"
  val NON_SCOTTISH = "England, Northern Ireland or Wales"
  val mockConfig: Configuration = mock[Configuration]
  val mockEnvironment: Environment = Environment(mock[File], mock[ClassLoader], Mode.Test)
  val mockAuthConnector = mock[AuthConnector]
  val mockUserDetailsConnector = mock[UserDetailsConnector]
  val mockSessionService = mock[SessionService]
  private val enrolmentIdentifier = EnrolmentIdentifier("PSAID", "Z123456")
  private val enrolment = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier), state = "Activated")
  private val enrolments = new Enrolments(Set(enrolment))
  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)
  val name = MemberName("Jim", "McGill")
  val nino = MemberNino(RandomNino.generate)
  val dob = RasDate(Some("1"), Some("1"), Some("1999"))
  val memberDob = MemberDateOfBirth(dob)
  val residencyStatusResult = ResidencyStatusResult("", None, "", "", "", "", "")
  val postData = Json.obj("firstName" -> "Jim", "lastName" -> "McGill", "nino" -> nino, "dateOfBirth" -> dob)
  val rasSession = RasSession(name, nino, memberDob, residencyStatusResult, None)


  object TestResultsController extends ResultsController {
    val authConnector: AuthConnector = mockAuthConnector
    override val userDetailsConnector: UserDetailsConnector = mockUserDetailsConnector

    override val config: Configuration = mockConfig
    override val env: Environment = mockEnvironment

    override val sessionService = mockSessionService

    when(mockSessionService.hasUserDimissedUrBanner()(Matchers.any())).thenReturn(Future.successful(false))
    when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
  }

  private def doc(result: Future[Result]): Document = Jsoup.parse(contentAsString(result))

  "Results Controller" should {
    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(),any())).thenReturn(successfulRetrieval)

    when(mockUserDetailsConnector.getUserDetails(any())(any())).
      thenReturn(Future.successful(UserDetails(None, None, "", groupIdentifier = Some("group"))))

    "return 200 when match found" in {
      val result = TestResultsController.matchFound(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return 200 when match not found" in {
      val result = TestResultsController.noMatchFound(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML when match found" in {
      val result = TestResultsController.matchFound(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }

    "return HTML when match not found" in {
      val result = TestResultsController.noMatchFound(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }

    "contain correct title when match found" in {
      val result = TestResultsController.matchFound(fakeRequest)
      val doc = Jsoup.parse(contentAsString(result))
      doc.title shouldBe Messages("match.found.page.title")
    }

    "contain correct title when match not found" in {
      val result = TestResultsController.noMatchFound(fakeRequest)
      val doc = Jsoup.parse(contentAsString(result))
      doc.title shouldBe Messages("match.not.found.page.title")
    }

    "contain customer details and residency status when match found and CY and CY+1 is present" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(
        Some(
          RasSession(name, nino, memberDob,
            ResidencyStatusResult(
              SCOTTISH, Some(NON_SCOTTISH),
              currentTaxYear.toString, (currentTaxYear + 1).toString,
              name.firstName + " " + name.lastName,
              memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy"),
              ""),None))
      ))
      val result = TestResultsController.matchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      val formattedName = name.firstName.capitalize + " " + name.lastName.capitalize

      doc(result).getElementById("top-content").text shouldBe Messages("match.found.top")
      doc(result).getElementById("sub-header").text shouldBe Messages("match.found.what.happens.next")
      doc(result).getElementById("cy-residency-status").text shouldBe Messages("scottish.taxpayer")
      doc(result).getElementById("ny-residency-status").text shouldBe Messages("non.scottish.taxpayer")
      doc(result).getElementById("choose-something-else").text shouldBe Messages("choose.something.else")
    }

    "contain correct ga events when match found and CY and CY+1 is present" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(
        Some(
          RasSession(name, nino, memberDob,
            ResidencyStatusResult(
              SCOTTISH, Some(NON_SCOTTISH),
              currentTaxYear.toString, (currentTaxYear + 1).toString,
              name.firstName + " " + name.lastName,
              memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy"),
              ""),None))
      ))
      val result = TestResultsController.matchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      doc(result).getElementById("choose-something-else").attr("data-journey-click") shouldBe "button - click:Residency status added CY & CY + 1:Choose something else to do"
      doc(result).getElementById("look-up-another-member-link").attr("data-journey-click") shouldBe "link - click:Residency status added CY & CY + 1:Look up another member"
    }

    "contain customer details and residency status when match found and only CY is present" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(
        Some(
          RasSession(name, nino, memberDob,
            ResidencyStatusResult(
              SCOTTISH, None,
              currentTaxYear.toString, (currentTaxYear + 1).toString,
              name.firstName + " " + name.lastName,
              memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy"),
              ""),None))
      ))
      val result = TestResultsController.matchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      val formattedName = name.firstName.capitalize + " " + name.lastName.capitalize

      doc(result).getElementById("top-content").text shouldBe Messages("match.found.top")
      doc(result).getElementById("sub-header").text shouldBe Messages("match.found.what.happens.next")
      doc(result).getElementById("bottom-content-cy").text shouldBe Messages("match.found.bottom.current-year.bottom", formattedName, (currentTaxYear + 1).toString, (currentTaxYear + 2).toString)
      doc(result).getElementById("cy-residency-status").text shouldBe Messages("scottish.taxpayer")
      doc(result).getElementById("choose-something-else").text shouldBe Messages("choose.something.else")
    }

    "contain correct ga event when match found and only CY is present" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(
        Some(
          RasSession(name, nino, memberDob,
            ResidencyStatusResult(
              SCOTTISH, None,
              currentTaxYear.toString, (currentTaxYear + 1).toString,
              name.firstName + " " + name.lastName,
              memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy"),
              ""),None))
      ))
      val result = TestResultsController.matchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      doc(result).getElementById("choose-something-else").attr("data-journey-click") shouldBe "button - click:Residency status added CY:Choose something else to do"
      doc(result).getElementById("look-up-another-member-link").attr("data-journey-click") shouldBe "link - click:Residency status added CY:Look up another member"
    }

    "display correct residency status for UK UK" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(
        Some(
          RasSession(name, nino, memberDob,
            ResidencyStatusResult(
              NON_SCOTTISH, Some(NON_SCOTTISH),
              currentTaxYear.toString, (currentTaxYear + 1).toString,
              name.firstName + " " + name.lastName,
              memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy"),
              ""),None))
      ))
      val result = TestResultsController.matchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))

      doc(result).getElementById("cy-residency-status").text shouldBe Messages("non.scottish.taxpayer")
      doc(result).getElementById("ny-residency-status").text shouldBe Messages("non.scottish.taxpayer")
    }

    "contain customer details and residency status when match not found" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(
        Some(
          RasSession(name, nino, memberDob,
            ResidencyStatusResult(
              "", None,
              currentTaxYear.toString, (currentTaxYear + 1).toString,
              name.firstName + " " + name.lastName,
              memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy"),
              ""),None))
      ))
      val result = TestResultsController.noMatchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      doc(result).getElementById("match-not-found").text shouldBe Messages("member.details.not.found", "Jim McGill")
      doc(result).getElementById("subheader").text shouldBe Messages("match.not.found.subheader","Jim McGill")
      doc(result).getElementById("change-name").text shouldBe Messages("change.name") + " " + Messages("change")
      doc(result).getElementById("name").text shouldBe "Jim McGill"
      doc(result).getElementById("change-nino").text shouldBe Messages("change.nino") + " " + Messages("change")
      doc(result).getElementById("nino").text shouldBe nino.nino
      doc(result).getElementById("change-dob").text shouldBe Messages("change.dob") + " " + Messages("change")
      doc(result).getElementById("dob").text shouldBe memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy")
      doc(result).getElementById("choose-something-else").text shouldBe Messages("choose.something.else")
    }

    "contain what to do next section when match not found" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(
        Some(
          RasSession(name, nino, memberDob,
            ResidencyStatusResult(
              "", None,
              currentTaxYear.toString, (currentTaxYear + 1).toString,
              name.firstName + " " + name.lastName,
              memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy"),
              ""),None))
      ))
      val result = TestResultsController.noMatchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      doc(result).getElementById("what-to-do").text shouldBe Messages("match.not.found.what.to.do", Messages("contact.hmrc", "Jim McGill"))
    }

    "contain ga event data when match not found " in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(
        Some(
          RasSession(name, nino, memberDob,
            ResidencyStatusResult(
              "", None,
              currentTaxYear.toString, (currentTaxYear + 1).toString,
              name.firstName + " " + name.lastName,
              memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy"),
              ""),None))
      ))
      val result = TestResultsController.noMatchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      doc(result).getElementById("back").attr("data-journey-click") shouldBe "navigation - link:Users details not found:Back"
      doc(result).getElementById("change-name-link").attr("data-journey-click") shouldBe "link - click:User details not found:Change Name"
      doc(result).getElementById("change-nino-link").attr("data-journey-click") shouldBe "link - click:User details not found:Change NINO"
      doc(result).getElementById("change-dob-link").attr("data-journey-click") shouldBe "link - click:User details not found:Change DOB"
      doc(result).getElementById("choose-something-else").attr("data-journey-click") shouldBe "button - click:User details not found:Choose something else to do"
    }

    "redirect to global error page when no session data is returned on match found" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(None))
      val result = TestResultsController.matchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("global-error")
    }

    "redirect to global error page when no session data is returned on match not found" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(None))
      val result = TestResultsController.noMatchFound.apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("global-error")
    }

    "return to member dob page when back link is clicked" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(
        Some(
          RasSession(name, nino, memberDob,
            ResidencyStatusResult(
              NON_SCOTTISH, Some(NON_SCOTTISH),
              currentTaxYear.toString, (currentTaxYear + 1).toString,
              name.firstName + " " + name.lastName,
              memberDob.dateOfBirth.asLocalDate.toString("d MMMM yyyy"),
              ""),None))
      ))
      val result = TestResultsController.back.apply(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/member-date-of-birth")
    }

    "redirect to global error when no sessino and back link is clicked" in {
      when(mockSessionService.fetchRasSession()(any())).thenReturn(Future.successful(None))
      val result = TestResultsController.back.apply(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/global-error")
    }

  }
}