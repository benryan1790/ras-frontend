/*
 * Copyright 2019 HM Revenue & Customs
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

import connectors.ResidencyStatusAPIConnector
import metrics.Metrics
import models._
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import services.AuditService
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.time.TaxYearResolver

trait RasResidencyCheckerController extends RasController {

  val residencyStatusAPIConnector : ResidencyStatusAPIConnector
  val auditService: AuditService

  val SCOTTISH = "scotResident"
  val NON_SCOTTISH = "otherUKResident"

  def submitResidencyStatus(session: RasSession, userId: String)(implicit request: Request[AnyContent], hc: HeaderCarrier) = {

    val timer = Metrics.responseTimer.time()
    val memberDetails = MemberDetails(session.name, session.nino.nino, session.dateOfBirth.dateOfBirth)

    residencyStatusAPIConnector.getResidencyStatus(memberDetails).map { rasResponse =>
      val formattedName = session.name.firstName + " " + session.name.lastName
      val formattedDob = session.dateOfBirth.dateOfBirth.asLocalDate.toString("d MMMM yyyy")
      val cyResidencyStatus = extractResidencyStatus(rasResponse.currentYearResidencyStatus)
      val nyResidencyStatus: Option[String] =
        if (rasResponse.nextYearForecastResidencyStatus.nonEmpty)
          Some(extractResidencyStatus(rasResponse.nextYearForecastResidencyStatus.get))
        else
          None

      if (cyResidencyStatus.isEmpty) {
        Logger.error("[DobController][post] An unknown residency status was returned")
        Redirect(routes.ErrorController.renderGlobalErrorPage())
      }
      else {
        Logger.info("[DobController][post] Match found")

        timer.stop()

        val residencyStatusResult =
          ResidencyStatusResult(
            cyResidencyStatus, nyResidencyStatus,
            TaxYearResolver.currentTaxYear.toString,
            (TaxYearResolver.currentTaxYear + 1).toString,
            formattedName, formattedDob, memberDetails.nino)

        sessionService.cacheResidencyStatusResult(residencyStatusResult)

        auditResponse(failureReason = None, nino = Some(memberDetails.nino),
          residencyStatus = Some(rasResponse),
          userId = userId)

        Redirect(routes.ResultsController.matchFound())
      }
    }.recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == FORBIDDEN =>
        auditResponse(failureReason = Some("MATCHING_FAILED"), nino = Some(memberDetails.nino),
          residencyStatus = None, userId = userId)
        Logger.info("[DobController][getResult] No match found from customer matching")
        timer.stop()
        Redirect(routes.ResultsController.noMatchFound())
      case e: Throwable =>
        auditResponse(failureReason = Some("INTERNAL_SERVER_ERROR"), nino = Some(memberDetails.nino),
          residencyStatus = None, userId = userId)
        Logger.error(s"[DobController][getResult] Customer Matching failed: ${e.getMessage}")
        Redirect(routes.ErrorController.renderGlobalErrorPage())
    }
  }

  private def extractResidencyStatus(residencyStatus: String) : String = {
    if(residencyStatus == SCOTTISH)
      Messages("scottish.taxpayer")
    else if(residencyStatus == NON_SCOTTISH)
      Messages("non.scottish.taxpayer")
    else
      ""
  }

  /**
    * Audits the response, if failure reason is None then residencyStatus is Some (sucess) and vice versa (failure).
    * @param failureReason Optional message, present if the journey failed, else not
    * @param nino Optional user identifier, present if the customer-matching-cache call was a success, else not
    * @param residencyStatus Optional status object returned from the HoD, present if the journey succeeded, else not
    * @param userId Identifies the user which made the request
    * @param request Object containing request made by the user
    * @param hc Headers
    */
  private def auditResponse(failureReason: Option[String], nino: Option[String],
                            residencyStatus: Option[ResidencyStatus], userId: String)
                           (implicit request: Request[AnyContent], hc: HeaderCarrier): Unit = {

    val ninoMap: Map[String, String] = nino.map(nino => Map("nino" -> nino)).getOrElse(Map())
    val nextYearStatusMap: Map[String, String] = if (residencyStatus.nonEmpty) residencyStatus.get.nextYearForecastResidencyStatus
      .map(nextYear => Map("NextCYStatus" -> nextYear)).getOrElse(Map())
    else Map()
    val auditDataMap: Map[String, String] = failureReason.map(reason => Map("successfulLookup" -> "false",
      "reason" -> reason)).
      getOrElse(Map(
        "successfulLookup" -> "true",
        "CYStatus" -> residencyStatus.get.currentYearResidencyStatus
      ) ++ nextYearStatusMap)

    auditService.audit(auditType = "ReliefAtSourceResidency",
      path = request.path,
      auditData = auditDataMap ++ Map("userIdentifier" -> userId, "requestSource" -> "FE_SINGLE") ++ ninoMap
    )
  }
}