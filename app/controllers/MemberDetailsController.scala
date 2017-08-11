/*
 * Copyright 2017 HM Revenue & Customs
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

import config.RasContextImpl
import connectors.{CustomerMatchingAPIConnector, ResidencyStatusAPIConnector}
import forms.MemberDetailsForm._
import models.{CustomerMatchingResponse, Link, ResidencyStatus, ResidencyStatusResult}
import play.api.Logger
import play.api.mvc.Action
import uk.gov.hmrc.play.http.Upstream4xxResponse
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.Future

object MemberDetailsController extends MemberDetailsController{
  override val customerMatchingAPIConnector = CustomerMatchingAPIConnector
  override val residencyStatusAPIConnector = ResidencyStatusAPIConnector
}

trait MemberDetailsController extends RasController {

  val customerMatchingAPIConnector: CustomerMatchingAPIConnector
  val residencyStatusAPIConnector : ResidencyStatusAPIConnector

  val SCOTTISH = "scotResident"
  val NON_SCOTTISH = "otherUKResident"
  val RAS = "ras"
  val NO_MATCH = "noMatch"
  val EMPTY_CMR = CustomerMatchingResponse(List(Link(RAS,"")))
  val NO_MATCH_CMR = CustomerMatchingResponse(List(Link(RAS,NO_MATCH)))


  implicit val context: config.RasContext = RasContextImpl

  def get = Action.async { implicit request =>
    Future.successful(Ok(views.html.member_details(form)))
  }

  def post = Action.async { implicit request =>

    form.bindFromRequest.fold(
      formWithErrors => {
        Logger.debug("[MemberDetailsController][post] Invalid form field passed")
        Future.successful(BadRequest(views.html.member_details(formWithErrors)))
      },
      memberDetails => {

        customerMatchingAPIConnector.findMemberDetails(memberDetails).flatMap { customerMatchingResponse =>

          residencyStatusAPIConnector.getResidencyStatus(extractResidencyStatusLink(customerMatchingResponse)).flatMap { rasResponse =>

            val name = memberDetails.firstName + " " + memberDetails.lastName
            val dateOfBirth = memberDetails.dateOfBirth.asLocalDate.toString("d MMMM yyyy")
            val cyResidencyStatus = extractResidencyStatus(rasResponse.currentYearResidencyStatus)
            val nyResidencyStatus = extractResidencyStatus(rasResponse.nextYearForecastResidencyStatus)

            if (cyResidencyStatus.isEmpty || nyResidencyStatus.isEmpty){
              Logger.info("[MemberDetailsController][post] An unknown residency status was returned")
              Future.successful(Redirect(routes.GlobalErrorController.get))
            }
            else {

              Logger.info("[MemberDetailsController][post] Match found")

              val residencyStatusResult = ResidencyStatusResult(cyResidencyStatus, nyResidencyStatus,
                TaxYearResolver.currentTaxYear.toString, (TaxYearResolver.currentTaxYear + 1).toString,
                name, dateOfBirth, memberDetails.nino)

              Future.successful(Ok(views.html.match_found(residencyStatusResult)))
            }
          }.recover{
            case e:Throwable =>
              Logger.error("[MemberDetailsController][getResult] Residency status failed")
              Redirect(routes.GlobalErrorController.get)
          }
        }.recover{
          case e:Upstream4xxResponse if(e.upstreamResponseCode == FORBIDDEN) =>
            Logger.info("[MemberDetailsController][getResult] No match found from customer matching")
            val name = memberDetails.firstName + " " + memberDetails.lastName
            val dateOfBirth = memberDetails.dateOfBirth.asLocalDate.toString("d MMMM yyyy")
            Ok(views.html.match_not_found(name, dateOfBirth, memberDetails.nino))
          case e:Throwable =>
            Logger.error("[MemberDetailsController][getResult] Customer Matching failed: " + e.getMessage )
            Redirect(routes.GlobalErrorController.get)
        }
      }
     )
  }

  private def extractResidencyStatusLink(customerMatchingResponse: CustomerMatchingResponse): String ={
    customerMatchingResponse._links.filter( _.name == RAS).head.href
  }

  private def extractResidencyStatus(residencyStatus: String) : String = {
    if(residencyStatus == SCOTTISH)
      Messages("scottish.taxpayer")
    else if(residencyStatus == NON_SCOTTISH)
      Messages("non.scottish.taxpayer")
    else
      ""
  }
}
