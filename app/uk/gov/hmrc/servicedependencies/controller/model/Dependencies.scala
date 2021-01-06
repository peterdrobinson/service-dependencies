/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.servicedependencies.controller.model

import java.time.Instant

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Dependencies(
    repositoryName        : String
  , libraryDependencies   : Seq[Dependency]
  , sbtPluginsDependencies: Seq[Dependency]
  , otherDependencies     : Seq[Dependency]
  , lastUpdated           : Instant
  )

object Dependencies {

  val writes: OWrites[Dependencies] =
    ( (__ \ "repositoryName"        ).write[String]
    ~ (__ \ "libraryDependencies"   ).lazyWrite(Writes.seq[Dependency](Dependency.writes))
    ~ (__ \ "sbtPluginsDependencies").lazyWrite(Writes.seq[Dependency](Dependency.writes))
    ~ (__ \ "otherDependencies"     ).lazyWrite(Writes.seq[Dependency](Dependency.writes))
    ~ (__ \ "lastUpdated"           ).write[Instant]
    )(unlift(Dependencies.unapply))

}
