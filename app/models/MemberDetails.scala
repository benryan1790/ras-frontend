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

package models


import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads, Writes}

case class MemberDetails(name: MemberName,
                         nino: String,
                         dateOfBirth: RasDate) {

  def asCustomerDetailsPayload = {
    Json.parse(
      s"""{
          "nino":"${nino.toUpperCase}",
          "firstName":"${name.firstName.capitalize}",
          "lastName":"${name.lastName.capitalize}",
          "dateOfBirth":"${dateOfBirth.asLocalDate.toString("yyyy-MM-dd")}"
        }
      """)
  }
}

object MemberDetails {
  implicit val memberDetailsReads: Reads[MemberDetails] = (
      (JsPath \ "name").read[MemberName]and
      (JsPath \ "nino").read[String] and
      (JsPath \ "dateOfBirth").read[RasDate]
    )(MemberDetails.apply _)

  implicit val memberDetailsWrites: Writes[MemberDetails] = (
    (JsPath \ "name").write[MemberName] and
      (JsPath \ "nino").write[String].contramap[String](nino => nino.toUpperCase) and
      (JsPath \ "dateOfBirth").write[RasDate]
    )(unlift(MemberDetails.unapply))
}

case class UserDetails(authProviderId: Option[String],
                       authProviderType: Option[String],
                       name: String,
                       lastName: Option[String] = None,
                       dateOfBirth: Option[String] = None,
                       postCode: Option[String] = None,
                       email: Option[String] = None,
                       affinityGroup: Option[String] = None,
                       agentCode: Option[String] = None,
                       agentId: Option[String] = None,
                       agentFriendlyName: Option[String] = None,
                       credentialRole: Option[String] = None,
                       description: Option[String] = None,
                       groupIdentifier: Option[String] = None)
