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

import org.joda.time.LocalDate
import play.api.libs.json.Json

case class RasDate(day: Option[String], month: Option[String], year: Option[String]){

  def asLocalDate: LocalDate = {
    new LocalDate(year.getOrElse("1").toInt, month.getOrElse("1").toInt, day.getOrElse("1").toInt)
  }

  def isInFuture: Boolean = {
    asLocalDate.isAfter(LocalDate.now)
  }

  override def toString = year + "-" + month + "-" + day

}

object RasDate {
  implicit val format = Json.format[RasDate]
}
