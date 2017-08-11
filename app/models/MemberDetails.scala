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

package models


import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads, Writes}

case class MemberDetails(firstName: String,
                         lastName: String,
                         nino: String,
                         dateOfBirth: RasDate)

object MemberDetails {
  implicit val customerDetailsReads: Reads[MemberDetails] = (
    (JsPath \ "nino").read[String] and
      (JsPath \ "firstName").read[String]and
      (JsPath \ "lastName").read[String] and
      (JsPath \ "dateOfBirth").read[RasDate]
    )(MemberDetails.apply _)

  implicit val memberDetailsWrites: Writes[MemberDetails] = (
      (JsPath \ "firstName").write[String] and
      (JsPath \ "lastName").write[String] and
      (JsPath \ "nino").write[String] and
      (JsPath \ "dateOfBirth").write[String].contramap[RasDate](date => date.toString)
    )(unlift(MemberDetails.unapply))
}