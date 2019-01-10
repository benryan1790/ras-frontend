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

package config

import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.StreamedResponse
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector => Auditing}
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import uk.gov.hmrc.play.http.ws._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.frontend.config.LoadAuditingConfig

object FrontendAuditConnector extends Auditing with AppName {
  override lazy val auditingConfig = LoadAuditingConfig("auditing")
}

trait WSHttp extends WSGet with HttpGet
  with WSPut with HttpPut
  with WSPost with HttpPost
  with WSDelete with HttpDelete
  with AppName {
  override val hooks = NoneRequired

  def buildRequestWithStream(uri: String)(implicit hc: HeaderCarrier): Future[StreamedResponse] = buildRequest(uri).stream()

  override def doPost[A](url: String, body: A, headers: Seq[(String, String)])(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = {
    buildRequest(url).withFollowRedirects(false).withHeaders(headers: _*).post(Json.toJson(body)).map(new WSHttpResponse(_))
  }
}

object WSHttp extends WSHttp

object FrontendAuthConnector extends PlayAuthConnector with ServicesConfig with WSHttp {
  val serviceUrl = baseUrl("auth")
  lazy val http = WSHttp
}
