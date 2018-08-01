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

import connectors.{ResidencyStatusAPIConnector, UserDetailsConnector}
import helpers.RandomNino
import helpers.helpers.I18nHelper
import models._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{atLeastOnce, verify, when}
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, _}
import play.api.{Configuration, Environment}
import services.{AuditService, SessionService}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.Upstream4xxResponse
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future


class MemberNinoControllerSpec extends UnitSpec with WithFakeApplication with I18nHelper with MockitoSugar{

  val fakeRequest = FakeRequest()
  val mockAuthConnector = mock[AuthConnector]
  val mockUserDetailsConnector = mock[UserDetailsConnector]
  val mockSessionService = mock[SessionService]
  val mockConfig = mock[Configuration]
  val mockEnvironment = mock[Environment]
  val mockRasConnector = mock[ResidencyStatusAPIConnector]
  val mockAuditService = mock[AuditService]

  val memberName: MemberName = MemberName("Jackie","Chan")
  val memberNino = MemberNino("AB123456C")
  val memberDob = MemberDateOfBirth(RasDate(Some("12"),Some("12"),Some("2012")))
  val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""),None)
  val postData = Json.obj("nino" -> RandomNino.generate)

  private val enrolmentIdentifier = EnrolmentIdentifier("PSAID", "Z123456")
  private val enrolment = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier), state = "Activated")
  private val enrolments = new Enrolments(Set(enrolment))
  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)

  val SCOTTISH = "scotResident"
  val NON_SCOTTISH = "otherUKResident"

  object TestMemberNinoController extends MemberNinoController{
    val authConnector: AuthConnector = mockAuthConnector
    override val userDetailsConnector: UserDetailsConnector = mockUserDetailsConnector
    override val sessionService = mockSessionService
    override val config: Configuration = mockConfig
    override val env: Environment = mockEnvironment
    override val residencyStatusAPIConnector: ResidencyStatusAPIConnector = mockRasConnector
    override val auditService: AuditService = mockAuditService

    when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
    when(mockSessionService.cacheNino(Matchers.any())(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
  }

  "MemberNinoController get" should {

    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(),any())).thenReturn(successfulRetrieval)

    when(mockUserDetailsConnector.getUserDetails(any())(any())).
      thenReturn(Future.successful(UserDetails(None, None, "", groupIdentifier = Some("group"))))

    "return ok" when {
      "called" in {
        val result = TestMemberNinoController.get()(fakeRequest)
        status(result) shouldBe OK
      }
    }

    "contain correct page elements and content" when {
      "rendered" in {
        val result = TestMemberNinoController.get()(fakeRequest)
        doc(result).title shouldBe Messages("member.nino.page.title")
        doc(result).getElementById("header").text shouldBe Messages("member.nino.page.header","Jackie Chan")
        doc(result).getElementById("nino_hint").text shouldBe Messages("nino.hint")
        assert(doc(result).getElementById("nino").attr("input") != null)
        doc(result).getElementById("continue").text shouldBe Messages("continue")
      }

      "rendered but no cached data exists" in {
        when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestMemberNinoController.get()(fakeRequest)
        doc(result).title shouldBe Messages("member.nino.page.title")
        doc(result).getElementById("header").text shouldBe Messages("member.nino.page.header",Messages("member"))
      }

      "contain the correct ga data when edit mode is false" in {
        val result = TestMemberNinoController.get()(fakeRequest)
        doc(result).getElementById("continue").attr("data-journey-click") shouldBe "button - click:What is their NINO?:Continue"
        doc(result).getElementsByClass("link-back").attr("data-journey-click") shouldBe "navigation - link:What is their NINO?:Back"
      }

      "contain the correct ga data when edit mode is true" in {
        val result = TestMemberNinoController.get(true)(fakeRequest)
        doc(result).getElementById("continue").attr("data-journey-click") shouldBe "button - click:What is their NINO?:Continue and submit"
      }
    }

  }

  "MemberNinoController post" should {

    "respond to POST /relief-at-source/member-national-insurance-number" in {
      val result = route(fakeApplication, FakeRequest(POST, "/relief-at-source/member-national-insurance-number"))
      status(result.get) should not equal (NOT_FOUND)
    }

    "return bad request with session name when form error is present and session contains a name" in {
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val postData = Json.obj(
        "nino" -> RandomNino.generate.substring(3))
      val result = TestMemberNinoController.post().apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) should equal(BAD_REQUEST)
      doc(result).getElementById("header").text shouldBe Messages("member.nino.page.header", "Jackie Chan")
    }

    "return bad request with member as name when form error is present and session contains no name" in {
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(None))
      val postData = Json.obj(
        "nino" -> RandomNino.generate.substring(3))
      val result = TestMemberNinoController.post().apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) should equal(BAD_REQUEST)
      doc(result).getElementById("header").text shouldBe Messages("member.nino.page.header", Messages("member"))
    }

    "return bad request when form error present with special characters" in {
      val postData = Json.obj(
        "nino" -> "AB123%56C")
      val result = TestMemberNinoController.post().apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) should equal(BAD_REQUEST)
    }

    "redirect to dob page when nino cached and edit mode is false" in {
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
      val result = TestMemberNinoController.post().apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("member-date-of-birth")
    }

    "redirect to match found page when edit mode is true and matching successful" in {
      when(mockRasConnector.getResidencyStatus(any())(any())).thenReturn(Future.successful(ResidencyStatus(SCOTTISH, Some(NON_SCOTTISH))))
      when(mockSessionService.cacheNino(Matchers.any())(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))

      val result = TestMemberNinoController.post(true).apply(fakeRequest.withJsonBody(Json.toJson(postData)))

      status(result) should equal(SEE_OTHER)
      redirectLocation(result).get should include("/member-residency-status")

      verify(mockSessionService, atLeastOnce()).cacheNino(Matchers.any())(Matchers.any())
    }

    "redirect to no match found page when edit mode is true and matching failed" in {
      when(mockRasConnector.getResidencyStatus(any())(any())).thenReturn(Future.failed(Upstream4xxResponse("Member not found", 403, 403)))

      val result = TestMemberNinoController.post(true).apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) should equal(SEE_OTHER)
      redirectLocation(result).get should include("/no-residency-status-displayed")

      verify(mockSessionService, atLeastOnce()).cacheNino(Matchers.any())(Matchers.any())
    }

    "redirect to technical error page if nino is not cached" in {
      when(mockSessionService.cacheNino(Matchers.any())(Matchers.any())).thenReturn(Future.successful(None))
      val result = TestMemberNinoController.post().apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("global-error")
    }

  }

  "return to member name page when back link is clicked and edit mode is false" in {
    when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
    val result = TestMemberNinoController.back().apply(FakeRequest())
    status(result) shouldBe SEE_OTHER
    redirectLocation(result).get should include("/member-name")
  }

  "return to match not found page when back link is clicked and edit mode is true" in {
    when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
    val result = TestMemberNinoController.back(true).apply(FakeRequest())
    status(result) shouldBe SEE_OTHER
    redirectLocation(result).get should include("/no-residency-status-displayed")
  }

  "redirect to global error page navigating back with no session" in {
    when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(None))
    val result = TestMemberNinoController.back().apply(FakeRequest())
    status(result) shouldBe SEE_OTHER
    redirectLocation(result).get should include("global-error")
  }

  private def doc(result: Future[Result]): Document = Jsoup.parse(contentAsString(result))

}
