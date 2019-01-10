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

package connectors

import config.WSHttp
import models._
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext

trait UserDetailsConnector extends ServicesConfig with UserDetailsJsonFormats {

  val httpGet: HttpGet = WSHttp

  def getUserDetails(url: String)(implicit hc: HeaderCarrier): Future[UserDetails] = {
    httpGet.GET[UserDetails](url)(implicitly, hc, MdcLoggingExecutionContext.fromLoggingDetails(hc))
  }

}

object UserDetailsConnector extends UserDetailsConnector
