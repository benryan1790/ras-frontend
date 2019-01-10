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

package helpers

package helpers

import play.api.Play.current

trait I18nHelper {
  def Messages(key: String, args: Any*): String = play.api.i18n.Messages.apply(key,args: _*)(implicitAppMessages)

  implicit def implicitAppMessages : play.api.i18n.Messages = play.api.i18n.Messages.Implicits.applicationMessages
}

object I18nHelper extends I18nHelper

