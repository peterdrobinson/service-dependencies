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

import java.time.Instant

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

sealed trait SlugInfoFlag { def asString: String }
object SlugInfoFlag {
  case object Latest          extends SlugInfoFlag { val asString = "latest"         }
  case object Production      extends SlugInfoFlag { val asString = "production"     }
  case object ExternalTest    extends SlugInfoFlag { val asString = "external test"  }
  case object Staging         extends SlugInfoFlag { val asString = "staging"        }
  case object QA              extends SlugInfoFlag { val asString = "qa"             }
  case object Integration     extends SlugInfoFlag { val asString = "integration"    }
  case object Development     extends SlugInfoFlag { val asString = "development"    }

  val values: List[SlugInfoFlag] = List(Latest, Production, ExternalTest, Staging, QA, Integration, Development)

  def parse(s: String): Option[SlugInfoFlag] = {
    if (s.equalsIgnoreCase("externaltest"))
      Some(ExternalTest)
    else
      values.find(_.asString.equalsIgnoreCase(s))
  }
}

case class SlugDependency(
  path       : String,
  version    : Version,
  group      : String,
  artifact   : String,
  meta       : String = ""
)

object SlugDependency {
  val format = {
    implicit val vf  = Version.format
    ( (__ \ "path"    ).format[String]
    ~ (__ \ "version" ).format[Version]
    ~ (__ \ "group"   ).format[String]
    ~ (__ \ "artifact").format[String]
    ~ (__ \ "meta"    ).format[String]
    )(SlugDependency.apply, unlift(SlugDependency.unapply))
  }
}

case class JavaInfo(
  version : String,
  vendor  : String,
  kind    : String
)

case class SlugInfo(
  uri                 : String,
  created             : Instant,
  name                : String,
  version             : Version,
  teams               : List[String],
  runnerVersion       : String,
  classpath           : String,
  java                : JavaInfo,
  sbtVersion          : Option[String],
  repoUrl             : Option[String],
  dependencies        : List[SlugDependency],
  dependencyDotCompile: String,
  dependencyDotTest   : String,
  dependencyDotBuild  : String,
  applicationConfig   : String,
  slugConfig          : String,
) {
  lazy val classpathOrderedDependencies: List[SlugDependency] =
    classpath.split(":")
      .map(_.replace("$lib_dir/", s"./$name-$version/lib/"))
      .toList
      .flatMap(path => dependencies.filter(_.path == path))
}

case class DependencyConfig(
    group   : String
  , artefact: String
  , version : String
  , configs : Map[String, String]
  )

trait MongoSlugInfoFormats {

  val javaInfoFormat: OFormat[JavaInfo] =
    Json.format[JavaInfo]

  def ignore[A] = OWrites[A](_ => Json.obj())

  val slugInfoFormat: OFormat[SlugInfo] = {
    implicit val vf  = Version.format
    implicit val sdf = SlugDependency.format
    implicit val jif = javaInfoFormat
    ( (__ \ "uri"              ).format[String]
    ~ (__ \ "created"          ).format[Instant](MongoJavatimeFormats.instantFormat)
    ~ (__ \ "name"             ).format[String]
    ~ (__ \ "version"          ).format[Version]
    ~ OFormat( Reads.pure(List.empty[String])
             , ignore[List[String]]
             )
    ~ (__ \ "runnerVersion"            ).format[String]
    ~ (__ \ "classpath"                ).format[String]
    ~ (__ \ "java"                     ).format[JavaInfo]
    ~ (__ \ "sbtVersion"               ).formatNullable[String]
    ~ (__ \ "repoUrl"                  ).formatNullable[String]
    ~ (__ \ "dependencies"             ).format[List[SlugDependency]] // this has been replaced by dependencyDot, but is still needed for Java slugs
    ~ (__ \ "dependencyDot" \ "compile").formatWithDefault[String]("")
    ~ (__ \ "dependencyDot" \ "test"   ).formatWithDefault[String]("")
    ~ (__ \ "dependencyDot" \ "build"  ).formatWithDefault[String]("")
    ~ (__ \ "applicationConfig"        ).formatWithDefault[String]("")
    ~ (__ \ "slugConfig"               ).formatWithDefault[String]("")
    )(SlugInfo.apply, unlift(SlugInfo.unapply))
  }

  val jdkVersionFormat: OFormat[JDKVersion] =
    ( (__ \ "name"   ).format[String]
    ~ (__ \ "version").format[String]
    ~ (__ \ "vendor" ).formatWithDefault[String]("Oracle")
    ~ (__ \ "kind"   ).formatWithDefault[String]("JDK")
    )(JDKVersion.apply, unlift(JDKVersion.unapply))

  val groupArtefactsFormat: OFormat[GroupArtefacts] =
    ( (__ \ "group"    ).format[String]
    ~ (__ \ "artifacts").format[List[String]]
    )(GroupArtefacts.apply, unlift(GroupArtefacts.unapply))

  val dependencyConfigFormat: OFormat[DependencyConfig] =
    ( (__ \ "group"   ).format[String]
    ~ (__ \ "artefact").format[String]
    ~ (__ \ "version" ).format[String]
    ~ (__ \ "configs" ).format[Map[String, String]]
                       .inmap[Map[String, String]]( _.map { case (k, v) => (k.replaceAll("_DOT_", "."    ), v) }  // for mongo < 3.6 compatibility - '.' and '$'' not permitted in keys
                                                  , _.map { case (k, v) => (k.replaceAll("\\."  , "_DOT_"), v) }
                                                  )
    )(DependencyConfig.apply, unlift(DependencyConfig.unapply))

  val schema =
    """
    { bsonType: "object"
    , required: [ "uri"
                , "created"
                , "name"
                , "version"
                , "runnerVersion"
                , "classpath"
                , "java"
                , "dependencies"
                ]
    , properties:
      { uri              : { bsonType: "string" }
      , created          : { bsonType: "date"   }
      , name             : { bsonType: "string" }
      , version          : { bsonType: "string" }
      , runnerVersion    : { bsonType: "string" }
      , classpath        : { bsonType: "string" }
      , java             : { bsonType: "object"
                           , required: [ "version" ]
                           , properties:
                             { version: { bsonType: "string" }
                             , vendor : { bsonType: "string" }
                             , kind   : { bsonType: "string" }
                             }
                           }
      , dependencies     : { bsonType: "array"
                           , items   : { bsonType: "object"
                                       , required: [ "path", "version", "group", "artifact" ]
                                       , properties:
                                         { path    : { bsonType: "string" }
                                         , version : { bsonType: "string" }
                                         , group   : { bsonType: "string" }
                                         , artifact: { bsonType: "string" }
                                         , meta    : { bsonType: "string" }
                                         }
                                       }
                           }
      , applicationConfig: { bsonType: "string" }
      , slugConfig       : { bsonType: "string" }
      }
    }
    """
}

object MongoSlugInfoFormats extends MongoSlugInfoFormats

trait ApiSlugInfoFormats {
  val javaInfoFormat: OFormat[JavaInfo] =
    Json.format[JavaInfo]

  val slugInfoFormat: OFormat[SlugInfo] = {
    implicit val vf  = Version.format
    implicit val jif = javaInfoFormat
    implicit val sdf = SlugDependency.format
    ( (__ \ "uri"                      ).format[String]
    ~ (__ \ "created"                  ).format[Instant]
    ~ (__ \ "name"                     ).format[String]
    ~ (__ \ "version"                  ).format[Version]
    ~ (__ \ "teams"                    ).formatWithDefault[List[String]](List.empty)
    ~ (__ \ "runnerVersion"            ).format[String]
    ~ (__ \ "classpath"                ).format[String]
    ~ (__ \ "java"                     ).format[JavaInfo]
    ~ (__ \ "sbtVersion"               ).formatNullable[String]
    ~ (__ \ "repoUrl"                  ).formatNullable[String]
    ~ (__ \ "dependencies"             ).format[List[SlugDependency]]
    ~ (__ \ "dependencyDot" \ "compile").format[String]
    ~ (__ \ "dependencyDot" \ "test"   ).format[String]
    ~ (__ \ "dependencyDot" \ "build"  ).format[String]
    ~ (__ \ "applicationConfig"        ).format[String]
    ~ (__ \ "slugConfig"               ).format[String]
    )(SlugInfo.apply, unlift(SlugInfo.unapply))
  }

  val dependencyConfigFormat: OFormat[DependencyConfig] =
    ( (__ \ "group"   ).format[String]
    ~ (__ \ "artefact").format[String]
    ~ (__ \ "version" ).format[String]
    ~ (__ \ "configs" ).format[Map[String, String]]
    )(DependencyConfig.apply, unlift(DependencyConfig.unapply))
}

object ApiSlugInfoFormats extends ApiSlugInfoFormats
