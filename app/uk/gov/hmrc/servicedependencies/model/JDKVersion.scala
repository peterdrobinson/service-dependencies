/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.servicedependencies.model

import play.api.libs.json.{__, OFormat}
import play.api.libs.functional.syntax._

case class JDKVersion(
  name   : String,
  version: String,
  vendor : String,
  kind   : String
)

trait JDKVersionFormats {

  val jdkVersionFormat: OFormat[JDKVersion] =
    ( (__ \ "name"   ).format[String]
    ~ (__ \ "version").format[String]
    ~ (__ \ "vendor" ).format[String]
    ~ (__ \ "kind"   ).format[String]
    )(JDKVersion.apply, unlift(JDKVersion.unapply))
}

object JDKVersionFormats extends JDKVersionFormats
