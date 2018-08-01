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
import helpers.helpers.I18nHelper
import models._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, contentType, _}
import play.api.{Configuration, Environment}
import services.{AuditService, SessionService}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class MemberNameControllerSpec extends UnitSpec with WithFakeApplication with I18nHelper with MockitoSugar {

  implicit val headerCarrier = HeaderCarrier()

  val fakeRequest = FakeRequest()
  val mockAuthConnector = mock[AuthConnector]
  val mockUserDetailsConnector = mock[UserDetailsConnector]
  val mockSessionService = mock[SessionService]
  val mockConfig = mock[Configuration]
  val mockEnvironment = mock[Environment]
  val mockRasConnector = mock[ResidencyStatusAPIConnector]
  val mockAuditService = mock[AuditService]

  private val enrolmentIdentifier = EnrolmentIdentifier("PSAID", "Z123456")
  private val enrolment = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier), state = "Activated")
  private val enrolments = new Enrolments(Set(enrolment))
  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)
  val memberName = MemberName("Jackie","Chan")
  val memberNino = MemberNino("AB123456C")
  val memberDob = MemberDateOfBirth(RasDate(Some("12"),Some("12"),Some("2012")))
  val rasSession = RasSession(memberName, memberNino, memberDob, ResidencyStatusResult("",None,"","","","",""), None)
  val postData = Json.obj("firstName" -> "Jim", "lastName" -> "McGill")

  val SCOTTISH = "scotResident"
  val NON_SCOTTISH = "otherUKResident"

  object TestMemberNameController extends MemberNameController{
    val authConnector: AuthConnector = mockAuthConnector
    override val userDetailsConnector: UserDetailsConnector = mockUserDetailsConnector
    override val sessionService = mockSessionService
    override val config: Configuration = mockConfig
    override val env: Environment = mockEnvironment
    override val residencyStatusAPIConnector: ResidencyStatusAPIConnector = mockRasConnector
    override val auditService: AuditService = mockAuditService

    when(mockSessionService.cacheName(Matchers.any())(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
    when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
  }

  private def doc(result: Future[Result]): Document = Jsoup.parse(contentAsString(result))

  // and finally the tests

  "MemberNameController" should {

    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(),any())).thenReturn(successfulRetrieval)

    when(mockUserDetailsConnector.getUserDetails(any())(any())).
      thenReturn(Future.successful(UserDetails(None, None, "", groupIdentifier = Some("group"))))


    "return 200" in {
      val result = TestMemberNameController.get()(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML" in {
      val result = TestMemberNameController.get()(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }

  "member name page" should {

    "contain correct title and header" in {
      val result = TestMemberNameController.get()(fakeRequest)
      doc(result).title shouldBe Messages("member.name.page.title")
      doc(result).getElementById("header").text shouldBe Messages("member.name.page.header")
    }

    "contain correct field labels" in {
      val result = TestMemberNameController.get()(fakeRequest)
      doc(result).getElementById("firstName_label").text shouldBe Messages("first.name").capitalize
      doc(result).getElementById("lastName_label").text shouldBe Messages("last.name").capitalize
    }

    "contain correct input fields" in {
      val result = TestMemberNameController.get()(fakeRequest)
      assert(doc(result).getElementById("firstName").attr("input") != null)
      assert(doc(result).getElementById("lastName").attr("input") != null)
    }

    "contain continue button" in {
      val result = TestMemberNameController.get()(fakeRequest)
      doc(result).getElementById("continue").text shouldBe Messages("continue")
    }

    "fill in form if cache data is returned" in {
      val result = TestMemberNameController.get()(fakeRequest)
      doc(result).getElementById("firstName").value.toString should include(memberName.firstName)
      doc(result).getElementById("lastName").value.toString should include(memberName.lastName)
    }

    "present empty form when no cached data exists" in {
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(None))
      val result = await(TestMemberNameController.get()(fakeRequest))
      assert(doc(result).getElementById("firstName").attr("value").equals(""))
      assert(doc(result).getElementById("lastName").attr("value").equals(""))
    }

    "contain the correct ga data when edit mode is false" in {
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(None))
      val result = await(TestMemberNameController.get()(fakeRequest))
      assert(doc(result).getElementById("continue").attr("data-journey-click").equals("button - click:What is their name?:Continue"))
      assert(doc(result).getElementsByClass("link-back").attr("data-journey-click").equals("navigation - link:What is their name?:Back"))
    }

    "contain the correct ga data when edit mode is true" in {
      when(mockSessionService.fetchRasSession()(Matchers.any())).thenReturn(Future.successful(None))
      val result = await(TestMemberNameController.get(true)(fakeRequest))
      assert(doc(result).getElementById("continue").attr("data-journey-click").equals("button - click:What is their name?:Continue and submit"))
    }
  }

  "Member name controller form submission" should {

    "respond to POST /relief-at-source/member-name" in {
      val result = route(fakeApplication, FakeRequest(POST, "/relief-at-source/member-name"))
      status(result.get) should not equal (NOT_FOUND)
    }

    "return bad request when form error present" in {
      val postData = Json.obj(
        "firstName" -> "",
        "lastName" -> "Esfandiari")
      val result = TestMemberNameController.post().apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) should equal(BAD_REQUEST)
    }

    "save details to cache" in {
      val result = await(TestMemberNameController.post().apply(fakeRequest.withJsonBody(Json.toJson(postData))))
      verify(mockSessionService, atLeastOnce()).cacheName(Matchers.any())(Matchers.any())
    }

    "redirect to nino page when name cached and edit mode is false" in {
      val session = RasSession(memberName, MemberNino(""), MemberDateOfBirth(RasDate(None,None,None)), ResidencyStatusResult("",None,"","","","",""),None)
      when(mockSessionService.cacheName(Matchers.any())(Matchers.any())).thenReturn(Future.successful(Some(session)))
      val result = TestMemberNameController.post().apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) shouldBe 303
      redirectLocation(result).get should include("/member-national-insurance-number")
    }

    "redirect to match found page when edit mode is true and matching successful" in {
      when(mockRasConnector.getResidencyStatus(any())(any())).thenReturn(Future.successful(ResidencyStatus(SCOTTISH, Some(NON_SCOTTISH))))
      when(mockSessionService.cacheName(Matchers.any())(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))

      val result = TestMemberNameController.post(true).apply(fakeRequest.withJsonBody(Json.toJson(postData)))

      status(result) should equal(SEE_OTHER)
      redirectLocation(result).get should include("/member-residency-status")

      verify(mockSessionService, atLeastOnce()).cacheName(Matchers.any())(Matchers.any())
    }

    "redirect to no match found page when edit mode is true and matching failed" in {
      when(mockRasConnector.getResidencyStatus(any())(any())).thenReturn(Future.failed(Upstream4xxResponse("Member not found", 403, 403)))

      val result = TestMemberNameController.post(true).apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) should equal(SEE_OTHER)
      redirectLocation(result).get should include("/no-residency-status-displayed")

      verify(mockSessionService, atLeastOnce()).cacheName(Matchers.any())(Matchers.any())
    }

    "redirect to technical error page if name is not cached" in {
      when(mockSessionService.cacheName(Matchers.any())(Matchers.any())).thenReturn(Future.successful(None))
      val result = TestMemberNameController.post().apply(fakeRequest.withJsonBody(Json.toJson(postData)))
      status(result) shouldBe 303
      redirectLocation(result).get should include("global-error")
    }

  }

  "Member name controller back" should {

    "return to chooseAnOption page when back link is clicked and edit mode is false" in {
      val result = TestMemberNameController.back().apply(fakeRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/")
    }

    "return to match not found page when back link is clicked and edit mode is true" in {
      val result = TestMemberNameController.back(true).apply(fakeRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/no-residency-status-displayed")
    }

  }
}
