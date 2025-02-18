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

package uk.gov.hmrc.servicedependencies.controller

import cats.data.EitherT
import cats.instances.all._
import com.google.inject.{Inject, Singleton}
import play.api.libs.functional.syntax._
import play.api.libs.json.{__, Json, OWrites}
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.servicedependencies.connector.ServiceConfigsConnector
import uk.gov.hmrc.servicedependencies.controller.model.{Dependencies, Dependency}
import uk.gov.hmrc.servicedependencies.model._
import uk.gov.hmrc.servicedependencies.persistence.{LatestVersionRepository, MetaArtefactRepository}
import uk.gov.hmrc.servicedependencies.service.{SlugDependenciesService, SlugInfoService, TeamDependencyService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceDependenciesController @Inject()(
  slugInfoService              : SlugInfoService
, slugDependenciesService      : SlugDependenciesService
, serviceConfigsConnector      : ServiceConfigsConnector
, teamDependencyService        : TeamDependencyService
, metaArtefactRepository       : MetaArtefactRepository
, latestVersionRepository      : LatestVersionRepository
, cc                           : ControllerComponents
)(implicit ec: ExecutionContext
) extends BackendController(cc) {

  implicit val dw: OWrites[Dependencies] = Dependencies.writes

  def dependenciesForTeam(teamName: String): Action[AnyContent] =
    Action.async { implicit request =>
      for {
        depsWithRules <- teamDependencyService.findAllDepsForTeam(teamName)
      } yield Ok(Json.toJson(depsWithRules))
  }

  def getServicesWithDependency(
    flag        : String,
    group       : String,
    artefact    : String,
    versionRange: String,
    scope       : Option[String]
  ): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val format = ApiServiceDependencyFormats.serviceDependencyFormat
      (for {
         f   <- EitherT.fromOption[Future](SlugInfoFlag.parse(flag), BadRequest(s"invalid flag '$flag'"))
         sc  <- scope match {
                  case None     => EitherT.pure[Future, Result](None)
                  case Some(sc) => EitherT.fromEither[Future](DependencyScope.parse(sc))
                                     .bimap(BadRequest(_), Some.apply)
                }
         vr  <- EitherT.fromOption[Future](BobbyVersionRange.parse(versionRange), BadRequest(s"invalid versionRange '$versionRange'"))
         res <- EitherT.right[Result] {
                  slugInfoService
                    .findServicesWithDependency(f, group, artefact, vr, sc)
                }
       } yield Ok(Json.toJson(res))
      ).merge
    }

  def getGroupArtefacts: Action[AnyContent] =
    Action.async {
      implicit val format = GroupArtefacts.apiFormat
      slugInfoService.findGroupsArtefacts
        .map(res => Ok(Json.toJson(res)))
    }

  def slugInfo(name: String, version: Option[String]): Action[AnyContent] =
    Action.async {
      (for {
         slugInfo        <- EitherT.fromOptionF(
                              version match {
                                case Some(version) => slugInfoService.getSlugInfo(name, Version(version))
                                case None          => slugInfoService.getSlugInfo(name, SlugInfoFlag.Latest)
                              },
                              NotFound("")
                            )
         // prefer graph data from meta-artefact if available
         optMetaArtefact <- EitherT.liftF[Future, Result, Option[MetaArtefact]](metaArtefactRepository.find(name, slugInfo.version))
         optModule       =  optMetaArtefact.flatMap(_.modules.headOption)
         slugInfo2       =  optMetaArtefact.fold(slugInfo)(ma => slugInfo.copy(
                              dependencyDotCompile = optModule.flatMap(_.dependencyDotCompile).getOrElse(""),
                              dependencyDotTest    = optModule.flatMap(_.dependencyDotTest).getOrElse(""),
                              dependencyDotBuild   = ma.dependencyDotBuild.getOrElse("")
                            ))
       } yield {
         implicit val f = ApiSlugInfoFormats.slugInfoFormat
         Ok(Json.toJson(slugInfo2))
       }
      ).merge
    }

  def dependenciesOfSlugForTeam(team: String, flag: String): Action[AnyContent] =
    Action.async { implicit request =>
      (for {
         f    <- EitherT.fromOption[Future](SlugInfoFlag.parse(flag), BadRequest(s"invalid flag '$flag'"))
         deps <- EitherT.liftF[Future, Result, Map[String, Seq[Dependency]]](
                   teamDependencyService.dependenciesOfSlugsForTeam(team, f)
                 )
       } yield {
         implicit val dw = Dependency.writes
         Ok(Json.toJson(deps))
       }
      ).merge
    }

  def findJDKForEnvironment(flag: String): Action[AnyContent] =
    Action.async {
      (for {
         f   <- EitherT.fromOption[Future](SlugInfoFlag.parse(flag), BadRequest(s"invalid flag '$flag'"))
         res <- EitherT.liftF[Future, Result, Seq[JDKVersion]](
                  slugInfoService.findJDKVersions(f)
                )
       } yield {
         implicit val jdkvf = JDKVersionFormats.jdkVersionFormat
         Ok(Json.toJson(res))
       }
      ).merge
    }

  def repositoryName(group: String, artefact: String, version: String): Action[AnyContent] =
    Action.async {
      metaArtefactRepository.findRepoNameByModule(group, artefact, Version(version))
        .map(_.fold(NotFound(""))(res => Ok(Json.toJson(res))))
    }

  def moduleDependencies(repositoryName: String, version: Option[String]): Action[AnyContent] =
    Action.async {
      (for {
         meta           <- EitherT.fromOptionF(
                             version match {
                               case Some(version)          => metaArtefactRepository.find(repositoryName, Version(version))
                               case None /* i.e. latest */ => metaArtefactRepository.find(repositoryName)
                             },
                             NotFound("")
                           )
         latestVersions <- EitherT.liftF[Future, Result, Seq[LatestVersion]](latestVersionRepository.getAllEntries)
         bobbyRules     <- EitherT.liftF[Future, Result, BobbyRules](serviceConfigsConnector.getBobbyRules)
       } yield {
         def toDependencies(name: String, scope: DependencyScope, dotFile: String) =
           slugDependenciesService.curatedLibrariesFromGraph(
             dotFile        = dotFile,
             rootName       = name,
             latestVersions = latestVersions,
             bobbyRules     = bobbyRules,
             scope          = scope
           )

        val sbtVersion =
          // sbt-versions will be the same for all modules,
          meta.modules.flatMap(_.sbtVersion.toSeq).headOption

         def dependencyIfMissing(
           dependencies: Seq[Dependency],
           group       : String,
           artefact    : String,
           versions    : Seq[Version],
           scope       : DependencyScope
         ): Seq[Dependency] =
           for {
             v <- versions
             if dependencies.find(dep => dep.group == group && dep.name == artefact).isEmpty
           } yield
             Dependency(
               name                = artefact,
               group               = group,
               currentVersion      = v,
               latestVersion       = latestVersions.find(d => d.name == artefact && d.group == group).map(_.version),
               bobbyRuleViolations = List.empty,
               importBy            = None,
               scope               = Some(scope)
             )

         val buildDependencies = meta.dependencyDotBuild.fold(Seq.empty[Dependency])(s => toDependencies(meta.name, DependencyScope.Build, s))

         val repository =
           Repository(
             name              = meta.name,
             version           = Some(meta.version),
             dependenciesBuild = buildDependencies ++
                                   dependencyIfMissing(
                                     buildDependencies,
                                     group    = "org.scala-sbt",
                                     artefact = "sbt",
                                     versions = sbtVersion.toSeq,
                                     scope    = DependencyScope.Build
                                   ),
             modules           = meta.modules
                                   .filter(_.publishSkip.fold(true)(!_))
                                   .map { m =>
                                     val compileDependencies = m.dependencyDotCompile.fold(Seq.empty[Dependency])(s => toDependencies(m.name, DependencyScope.Compile, s))
                                     val testDependencies    = m.dependencyDotTest   .fold(Seq.empty[Dependency])(s => toDependencies(m.name, DependencyScope.Test   , s))
                                     val scalaVersions       = m.crossScalaVersions.toSeq.flatten

                                     RepositoryModule(
                                       name                = m.name,
                                       group               = m.group,
                                       dependenciesCompile = compileDependencies ++
                                                               dependencyIfMissing(
                                                                 compileDependencies,
                                                                 group    = "org.scala-lang",
                                                                 artefact = "scala-library",
                                                                 versions = scalaVersions,
                                                                 scope    = DependencyScope.Compile
                                                               ),
                                       dependenciesTest    = testDependencies ++
                                                               dependencyIfMissing(
                                                                 testDependencies,
                                                                 group    = "org.scala-lang",
                                                                 artefact = "scala-library",
                                                                 versions = scalaVersions,
                                                                 scope    = DependencyScope.Test
                                                               ),
                                       crossScalaVersions  = m.crossScalaVersions
                                     )
                                   }
           )
         implicit val rw = Repository.writes
         Ok(Json.toJson(repository))
       }
      ).leftFlatMap(_ =>
        // fallback to data from curatedLibrariesOfSlug
        for {
          dependencies <- version match {
                            case None          => EitherT.fromOptionF(slugDependenciesService.curatedLibrariesOfSlug(repositoryName, SlugInfoFlag.Latest), NotFound(""))
                            case Some(version) => EitherT.fromOptionF(slugDependenciesService.curatedLibrariesOfSlug(repositoryName, Version(version)), NotFound(""))
                          }
        } yield {
          implicit val rw = Repository.writes
          Ok(
            Json.toJson(
              Repository(
                name              = repositoryName,
                version           = None,
                dependenciesBuild = dependencies.filter(_.scope == Some(DependencyScope.Build)),
                modules           = Seq(
                                      RepositoryModule(
                                        name                = repositoryName,
                                        group               = "uk.gov.hmrc",
                                        dependenciesCompile = dependencies.filter(_.scope == Some(DependencyScope.Compile)),
                                        dependenciesTest    = dependencies.filter(_.scope == Some(DependencyScope.Test)),
                                        crossScalaVersions  = None
                                      )
                                    )
              )
            )
          )
        }
      ).merge
    }

  def latestVersion(group: String, artefact: String): Action[AnyContent] =
    Action.async {
      latestVersionRepository.find(group, artefact)
        .map(_.fold(NotFound(""))(res => Ok(Json.toJson(res)(LatestVersion.apiWrites))))
    }
}

case class Repository(
  name             : String,
  version          : Option[Version], // optional since we don't have this when reshaping old data
  dependenciesBuild: Seq[Dependency],
  modules          : Seq[RepositoryModule]
)

object Repository {
  val writes: OWrites[Repository] = {
    implicit val dw  = Dependency.writes
    implicit val rmw = RepositoryModule.writes
    implicit val vf  = Version.format
    ( (__ \ "name"             ).write[String]
    ~ (__ \ "version"          ).writeNullable[Version]
    ~ (__ \ "dependenciesBuild").write[Seq[Dependency]]
    ~ (__ \ "modules"          ).write[Seq[RepositoryModule]]
    )(unlift(Repository.unapply))
  }
}

case class RepositoryModule(
  name               : String,
  group              : String,
  dependenciesCompile: Seq[Dependency],
  dependenciesTest   : Seq[Dependency],
  crossScalaVersions : Option[List[Version]]
)

object RepositoryModule {
  val writes: OWrites[RepositoryModule] = {
    implicit val dw = Dependency.writes
    implicit val vf  = Version.format
    ( (__ \ "name"               ).write[String]
    ~ (__ \ "group"              ).write[String]
    ~ (__ \ "dependenciesCompile").write[Seq[Dependency]]
    ~ (__ \ "dependenciesTest"   ).write[Seq[Dependency]]
    ~ (__ \ "crossScalaVersions" ).write[Seq[Version]].contramap[Option[Seq[Version]]](_.getOrElse(Seq.empty))
    )(unlift(RepositoryModule.unapply))
  }
}
